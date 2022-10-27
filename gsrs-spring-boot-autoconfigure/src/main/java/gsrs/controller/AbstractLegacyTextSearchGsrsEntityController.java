package gsrs.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.queryparser.classic.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gsrs.cache.GsrsCache;
import gsrs.controller.hateoas.GsrsLinkUtil;
import gsrs.controller.hateoas.IxContext;
import gsrs.legacy.GsrsSuggestResult;
import gsrs.legacy.LegacyGsrsSearchService;
import gsrs.security.hasAdminRole;
import gsrs.services.TextService;
import gsrs.springUtils.AutowireHelper;
import gsrs.springUtils.StaticContextAccessor;
import ix.core.models.BaseModel;
import ix.core.search.GsrsLegacySearchController;
import ix.core.search.SearchOptions;
import ix.core.search.SearchRequest;
import ix.core.search.SearchResult;
import ix.core.search.SearchResultContext;
import ix.core.search.bulk.BulkSearchService;
import ix.core.search.bulk.BulkSearchService.BulkQuerySummary;
import ix.core.search.bulk.SearchResultSummaryRecord;
import ix.core.search.text.FacetMeta;
import ix.core.search.text.TextIndexer;
import ix.core.util.EntityUtils;
import ix.utils.CallableUtil;
import ix.utils.Util;
import lombok.extern.slf4j.Slf4j;

/**
 * Extension to AbstractGsrsEntityController that adds support for the legacy TextIndexer
 * and related search routes that would use it including {@code /@facets} , {@code /search} and {@code /search/@facets} .
 *
 * @param <T>
 * @param <I>
 */
@Slf4j
public abstract class AbstractLegacyTextSearchGsrsEntityController<C extends AbstractLegacyTextSearchGsrsEntityController, T, I> extends AbstractGsrsEntityController<C, T,I> implements GsrsLegacySearchController {

//    public AbstractLegacyTextSearchGsrsEntityController(String context, IdHelper idHelper) {
//        super(context, idHelper);
//    }
//    public AbstractLegacyTextSearchGsrsEntityController(String context, Pattern idPattern) {
//        super(context, idPattern);
//    }
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    @Autowired
    private TextService textService;
       
    @Autowired    
	protected GsrsCache gsrscache;
    
    @Autowired
    private EntityLinks entityLinks;
    
    private final int BULK_SEARCH_DEFAULT_TOP = 1000;
    
    private final int BULK_SEARCH_DEFAULT_SKIP = 0;

    /**
     * Force a reindex of all entities of this entity type.
     * @param wipeIndex should the whole index be deleted before re-index begins;
     *                  defaults to {@code false}.
     * @return
     */
    @hasAdminRole
    @PostGsrsRestApiMapping(value="/@reindex", apiVersions = 1)
    public ResponseEntity forceFullReindex(@RequestParam(value= "wipeIndex", defaultValue = "false") boolean wipeIndex){
        getlegacyGsrsSearchService().reindexAndWait(wipeIndex);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    @GetGsrsRestApiMapping(value = "/search/@facets", apiVersions = 1)
    public FacetMeta searchFacetFieldDrilldownV1(@RequestParam("q") Optional<String> query,
                                                 @RequestParam("field") Optional<String> field,
                                                 @RequestParam("top") Optional<Integer> top,
                                                 @RequestParam("skip") Optional<Integer> skip,
                                                 HttpServletRequest request) throws ParseException, IOException {
        SearchOptions so = new SearchOptions.Builder()
                .kind(getEntityService().getEntityClass())
                .top(Integer.MAX_VALUE) // match Play GSRS
                .fdim(10)
                .fskip(0)
                .ffilter("")
                .withParameters(request.getParameterMap())
                .build();
        so = this.instrumentSearchOptions(so);

        TextIndexer.TermVectors tv= getlegacyGsrsSearchService().getTermVectorsFromQuery(query.orElse(null), so, field.orElse(null));
        return tv.getFacet(so.getFdim(), so.getFskip(), so.getFfilter(), StaticContextAccessor.getBean(IxContext.class).getEffectiveAdaptedURI(request).toString());


        //indexer.extractFullFacetQuery(this.query, this.options, field);
    }
    @GetGsrsRestApiMapping(value = "/@facets", apiVersions = 1)
    public FacetMeta searchFacetFieldV1(@RequestParam("field") Optional<String> field,
                                        @RequestParam("top") Optional<Integer> top,
                                        @RequestParam("skip") Optional<Integer> skip,
                                        HttpServletRequest request) throws ParseException, IOException {

        SearchOptions so = new SearchOptions.Builder()
                .fdim(10)
                .fskip(0)
                .ffilter("")
                .withParameters(Util.reduceParams(request.getParameterMap(),
                        "fdim", "fskip", "ffilter"))
                .build();
        
        so = this.instrumentSearchOptions(so);

        TextIndexer.TermVectors tv = getlegacyGsrsSearchService().getTermVectors(field);
        return tv.getFacet(so.getFdim(), so.getFskip(), so.getFfilter(), StaticContextAccessor.getBean(IxContext.class).getEffectiveAdaptedURI(request).toString());

    }

    /**
     * Get the implementation of {@link LegacyGsrsSearchService} for this entity type.
     * @return
     */
    protected abstract LegacyGsrsSearchService<T> getlegacyGsrsSearchService();

    /*
    GET     /suggest/@fields       ix.core.controllers.search.SearchFactory.suggestFields
GET     /suggest/:field       ix.core.controllers.search.SearchFactory.suggestField(field: String, q: String, max: Int ?= 10)
GET     /suggest       ix.core.controllers.search.SearchFactory.suggest(q: String, max: Int ?= 10)


     */
    @GetGsrsRestApiMapping("/suggest/@fields")
    public Collection<String> suggestFields() throws IOException {
        return getlegacyGsrsSearchService().getSuggestFields();
    }
    @GetGsrsRestApiMapping("/suggest")
    public Map<String, List<? extends GsrsSuggestResult>> suggest(@RequestParam(value ="q") String q, @RequestParam(value ="max", defaultValue = "10") int max) throws IOException {
        return getlegacyGsrsSearchService().suggest(q, max);
    }
    @GetGsrsRestApiMapping("/suggest/{field}")
    public List<? extends GsrsSuggestResult> suggestField(@PathVariable("field") String field,  @RequestParam("q") String q, @RequestParam(value ="max", defaultValue = "10") int max) throws IOException {
        return getlegacyGsrsSearchService().suggestField(field, q, max);
    }
    
    @GetGsrsRestApiMapping(value = "/search", apiVersions = 1)
    public ResponseEntity<Object> searchV1(@RequestParam("q") Optional<String> query,
                                           @RequestParam("top") Optional<Integer> top,
                                           @RequestParam("skip") Optional<Integer> skip,
                                           @RequestParam("fdim") Optional<Integer> fdim,
                                           HttpServletRequest request,
                                           @RequestParam Map<String, String> queryParameters){
        SearchRequest.Builder builder = new SearchRequest.Builder()
                .query(query.orElse(null))
                .kind(getEntityService().getEntityClass());

        top.ifPresent( t-> builder.top(t));
        skip.ifPresent( t-> builder.skip(t));
        fdim.ifPresent( t-> builder.fdim(t));

        SearchRequest searchRequest = builder.withParameters(request.getParameterMap())
                .build();

        this.instrumentSearchRequest(searchRequest);
        
        SearchResult result = null;
        try {
            result = getlegacyGsrsSearchService().search(searchRequest.getQuery(), searchRequest.getOptions() );
        } catch (Exception e) {
            return getGsrsControllerConfiguration().handleError(e, queryParameters);
        }
        
        SearchResult fresult=result;
        
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);        
        List results = (List) transactionTemplate.execute(stauts -> {
            //the top and skip settings  look wrong, because we're not skipping
            //anything, but it's actually right,
            //because the original request did the skipping.
            //This mechanism should probably be worked out
            //better, as it's not consistent.

            //Note that the SearchResult uses a LazyList,
            //but this is copying to a real list, this will
            //trigger direct fetches from the lazylist.
            //With proper caching there should be no further
            //triggered fetching after this.

            String viewType=queryParameters.get("view");
            if("key".equals(viewType)){
                List<ix.core.util.EntityUtils.Key> klist=new ArrayList<>(Math.min(fresult.getCount(),1000));
                fresult.copyKeysTo(klist, 0, top.orElse(10), true); 
                return klist;
            }else{
                List tlist = new ArrayList<>(top.orElse(10));
                fresult.copyTo(tlist, 0, top.orElse(10), true);
                return tlist;
            }
        });

        
        //even if list is empty we want to return an empty list not a 404
        ResponseEntity<Object> ret= new ResponseEntity<>(createSearchResponse(results, result, request), HttpStatus.OK);
        return ret;
    }
    
    @PostGsrsRestApiMapping(value="/@bulkQuery")
    public ResponseEntity<String> saveQueryList(@RequestBody String query,
    									@RequestParam("top") Optional<Integer> top,
  										@RequestParam("skip") Optional<Integer> skip,
  										HttpServletRequest request){
    	
    	int qTop = BULK_SEARCH_DEFAULT_TOP, qSkip = BULK_SEARCH_DEFAULT_SKIP;
    	if(top.isPresent()) 
    		qTop = top.get();
    	if(skip.isPresent()) 
    		qSkip = skip.get();
    	    	
    	List<String> queries = Arrays.asList(query.split("\\r?\\n|\\r"));
  	  
    	List<String> list = queries.stream()    			
    			.map(q->q.trim())
    			.filter(q->q.length()>0)
//    			.distinct()                            No need to be distinct
    			.collect(Collectors.toList());    	
    	
    	String queryStringToSave = list.stream().collect(Collectors.joining("\n"));
    	Long id = textService.saveTextString("bulkSearch", queryStringToSave);
    	
    	String uri = request.getRequestURL().toString(); 
    	if(request.getQueryString()!=null)
    		uri = uri + "?"+request.getQueryString();
    	else
    		uri = uri + "?top=" + qTop + "&skip=" + qSkip; 
    	    	
    	String returnJsonSrting = createJson(id, qTop, qSkip, list, uri);    	
 
        return new ResponseEntity<>(returnJsonSrting, HttpStatus.OK);
    }
    
    
    @PutGsrsRestApiMapping(value="/@bulkQuery")
    public ResponseEntity<String> updateQueryList(@RequestBody String query,
    									@RequestParam("id") String queryId,
    									@RequestParam("top") Optional<Integer> top,
  										@RequestParam("skip") Optional<Integer> skip,
  										HttpServletRequest request){
    	
    	Long id = Long.parseLong(queryId);
    	if(id < 0) {
    		return new ResponseEntity<>("Invalid ID " + id, HttpStatus.BAD_REQUEST);    		
    	}
    	
    	int qTop = BULK_SEARCH_DEFAULT_TOP, qSkip = BULK_SEARCH_DEFAULT_SKIP;
    	if(top.isPresent()) 
    		qTop = top.get();
    	if(skip.isPresent()) 
    		qSkip = skip.get();
    	    	
    	List<String> queries = Arrays.asList(query.split("\\r?\\n|\\r"));
  	  
    	List<String> list = queries.stream()    			
    			.map(q->q.trim())
    			.filter(q->q.length()>0)
//    			.distinct()                            No need to be distinct
    			.collect(Collectors.toList());    	
    	
    	String queryStringToSave = list.stream().collect(Collectors.joining("\n"));    	
    	
    	Long returnId = textService.updateTextString("bulkSearch", id, queryStringToSave);
    	
    	String uri = request.getRequestURL().toString(); 
    	if(request.getQueryString()!=null)
    		uri = uri + "?"+request.getQueryString();
    	else
    		uri = uri + "?top=" + qTop + "&skip=" + qSkip; 
    	    	
    	String returnJsonSrting = createJson(returnId, qTop, qSkip, list, uri);    	
 
        return new ResponseEntity<>(returnJsonSrting, HttpStatus.OK);
    }


    @GetGsrsRestApiMapping(value="/@bulkQuery")
    public ResponseEntity<String> getQueryList(@RequestParam String id,
    										   @RequestParam("top") Optional<Integer> top,
    										   @RequestParam("skip") Optional<Integer> skip,
    										   HttpServletRequest request){    	
    	
    	String queryString = textService.getText(id); 
    	int qTop = BULK_SEARCH_DEFAULT_TOP, qSkip = BULK_SEARCH_DEFAULT_SKIP;
    	if(top.isPresent()) 
    		qTop = top.get();
    	if(skip.isPresent()) 
    		qSkip = skip.get();
    	List<String> queries = Arrays.asList(queryString.split("\n"));
    	List<String> list = queries.stream()
    								.map(p->p.trim())
    								.collect(Collectors.toList());  
    	
    	String uri = request.getRequestURL().toString(); 
    	if(request.getQueryString()!=null)
    		uri = uri + "?" + request.getQueryString();
    	else
    		uri = uri + "?top=" + qTop + "&skip=" + qSkip; 
    	
    	String returnJson = createJson(Long.parseLong(id), qTop, qSkip, list, uri);
        return new ResponseEntity<>(returnJson, HttpStatus.OK);
    }
    
    @DeleteGsrsRestApiMapping(value="/@bulkQuery")
    public ResponseEntity<String> deleteQueryList(@RequestParam String id){    	
    	textService.deleteText(id); 	    	
        return new ResponseEntity<>(HttpStatus.OK);
    }
    
	@GetGsrsRestApiMapping(value = "/bulkSearch", apiVersions = 1)
	public ResponseEntity<Object> bulkSearch(@RequestParam("bulkQID") String queryListID,
			@RequestParam("q") Optional<String> query, @RequestParam("top") Optional<Integer> top,
			@RequestParam("skip") Optional<Integer> skip, @RequestParam("qTop") Optional<Integer> qTop,
			@RequestParam("qSkip") Optional<Integer> qSkip,@RequestParam("fdim") Optional<Integer> fdim,
			@RequestParam("searchOnIdentifiers") Optional<Boolean> searchOnIdentifiers,
			HttpServletRequest request, @RequestParam Map<String, String> queryParameters) {
		SearchRequest.Builder builder = new SearchRequest.Builder().query(query.orElse(null))
				.kind(getEntityService().getEntityClass());

		top.ifPresent(t -> builder.top(t));
		skip.ifPresent(t -> builder.skip(t));
		fdim.ifPresent(t -> builder.fdim(t));
		qTop.ifPresent(t -> builder.qTop(t));
		qSkip.ifPresent(t -> builder.qSkip(t));
		
		System.out.println("searchOnIdentifiers " + searchOnIdentifiers);
		searchOnIdentifiers.ifPresent(t -> builder.bulkSearchOnIdentifiers(t.booleanValue()));
				
		SearchRequest searchRequest = builder.withParameters(request.getParameterMap()).build();
		searchRequest = this.instrumentSearchRequest(searchRequest);

		BulkSearchService.SanitizedBulkSearchRequest sanitizedRequest = new BulkSearchService.SanitizedBulkSearchRequest();

		List<String> queries = new ArrayList<>();
		try {
			queries = gsrscache.getOrElse("/BulkID/" + queryListID, () -> {

				String queryString = textService.getText(queryListID);
				if (queryString.isEmpty()) {
					throw new RuntimeException("Cannot find bulk query ID. ");
				}
				return Arrays.asList(queryString.split("\n"));

			});
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return new ResponseEntity<>(e1.getMessage(), HttpStatus.NOT_FOUND);
		}

		sanitizedRequest.setQueries(queries);
		SearchOptions searchOptions = searchRequest.getOptions();
		SearchResultContext resultContext;
		try {
			resultContext = getlegacyGsrsSearchService().bulkSearch(sanitizedRequest, searchOptions);
			updateSearchContextGenerator(resultContext, queryParameters);

			// TODO: need to add support for qText in the "focused" version of
			// all structure searches. This may require some deeper changes.
			SearchResultContext focused = resultContext.getFocused(SearchOptions.DEFAULT_TOP, 0,
					searchOptions.getFdim(), "");
			if(resultContext.getKey() != null)
				focused.setKey(resultContext.getKey());
			return entityFactoryDetailedSearch(focused, false);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new ResponseEntity<>("Error during bulk search!", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
    
    
    private String createJson(Long id, int top, int skip, List<String> queries, String uri){
    	
    	List<String> sublist = new ArrayList<String>();
    	int endIndex = Math.min(top+skip,queries.size());    		
    	if(skip < queries.size())
    		sublist = queries.subList(skip, endIndex);
    	ObjectMapper mapper = new ObjectMapper();
    	ObjectNode baseNode = mapper.createObjectNode();   	   	
    	
    	baseNode.put("id", id);
    	baseNode.put("total", queries.size());
    	baseNode.put("count", sublist.size());
    	baseNode.put("top", top);
    	baseNode.put("skip", skip);    	
    	ArrayNode listNode = baseNode.putArray("queries");
    	sublist.forEach(listNode::add);    	   	
    	baseNode.put("_self", uri);
    	
    	return baseNode.toPrettyString();
    }
        
    protected abstract Object createSearchResponse(List<Object> results, SearchResult result, HttpServletRequest request);

    static String getOrderedKey (SearchResultContext context, SearchRequest request) {
        return "fetchResult/"+context.getId() + "/" + request.getOrderedSetSha1();
    }
    static String getKey (SearchResultContext context, SearchRequest request) {
        return "fetchResult/"+context.getId() + "/" + request.getDefiningSetSha1();
    }
    
    public SearchResult getResultFor(SearchResultContext ctx, SearchRequest req, boolean preserveOrder)
            throws IOException, Exception{

        final String key = (preserveOrder)? getOrderedKey(ctx,req):getKey (ctx, req);

        CallableUtil.TypedCallable<SearchResult> tc = CallableUtil.TypedCallable.of(() -> {
            Collection results = ctx.getResults();
            SearchRequest request = new SearchRequest.Builder()
                    .subset(results)
                    .options(req.getOptions())
                    .skip(0)
                    .top(results.size())
                    .query(req.getQuery())
                    .build();
            request=instrumentSearchRequest(request);

            SearchResult searchResult =null;

            if (results.isEmpty()) {
                searchResult= SearchResult.createEmptyBuilder(req.getOptions())
                        .build();
            }else{
                //katzelda : run it through the text indexer for the facets?
//                searchResult = SearchFactory.search (request);            	
                searchResult = getlegacyGsrsSearchService().search(request.getQuery(), request.getOptions(), request.getSubset());
                log.debug("Cache misses: "
                        +key+" size="+results.size()
                        +" class="+searchResult);
            }

            // make an alias for the context.id to this search
            // result
            searchResult.setKey(ctx.getId());
            return searchResult;
        }, SearchResult.class);

        if(ctx.isDetermined()) {
            return gsrscache.getOrElse(key, tc);
        }else {
            return tc.call();
        }
    }

    public ResponseEntity<Object> entityFactoryDetailedSearch(SearchResultContext context, boolean sync) throws InterruptedException, ExecutionException {
        context.setAdapter((srequest, ctx) -> {
            try {
                // TODO: technically this shouldn't be needed,
                // but something is getting lost in translation between 2.X and 3.0
                // and it's leading to some results coming back which are not substances.
                // This is particularly strange since there is an explicit subset which IS
                // all substances given.
            	
                srequest.getOptions().setKind(this.getEntityService().getEntityClass());
                SearchResult sr = getResultFor(ctx, srequest,true);
                
                if(ctx.getKey() != null) {                	
                	BulkQuerySummary summary = (BulkQuerySummary)gsrscache.getRaw("BulkSearchSummary/"+ctx.getKey());
                	if(summary!= null)
                		sr.setSummary(summary);                	
                }

                List<T> rlist = new ArrayList<>();

                sr.copyTo(rlist, srequest.getOptions().getSkip(), srequest.getOptions().getTop(), true); // synchronous
                for (T s : rlist) { 
                	if(s instanceof BaseModel) {
                		((BaseModel)s).setMatchContextProperty(gsrscache.getMatchingContextByContextID(ctx.getId(), EntityUtils.EntityWrapper.of(s).getKey().toRootKey()));
                	}
                }
                return sr;
            } catch (Exception e) {
                e.printStackTrace();
                throw new IllegalStateException("Error fetching search result", e);
            }
        });


        if (sync) {
            try {
                context.getDeterminedFuture().get(1, TimeUnit.MINUTES);
                HttpHeaders headers = new HttpHeaders();

                //TODO this should actually forward to "status(<key>)/results", but it's currently status/<key>/results
                headers.add("Location", GsrsLinkUtil.adapt(context.getKey(),entityLinks.linkFor(SearchResultContext.class).slash(context.getKey()).slash("results").withSelfRel())
                        .toUri().toString() );
                return new ResponseEntity<>(headers,HttpStatus.FOUND);
            } catch (TimeoutException e) {
                log.warn("Structure search timed out!", e);
            }
        }
        return new ResponseEntity<>(context, HttpStatus.OK);
    }

    protected void updateSearchContextGenerator(SearchResultContext resultContext, Map<String,String> queryParameters) {
        String oldURL = resultContext.getGeneratingUrl();
        if(oldURL!=null && !oldURL.contains("?")) {
            //we have to manually set the actual request uri here as it's the only place we know it!!
            //for some reason the spring boot methods to get the current quest  URI don't include the parameters
            //so we have to append them manually here from our controller
            StringBuilder queryParamBuilder = new StringBuilder();
            queryParameters.forEach((k,v)->{
                if(queryParamBuilder.length()==0){
                    queryParamBuilder.append("?");
                }else{
                    queryParamBuilder.append("&");
                }
                queryParamBuilder.append(k).append("=").append(v);
            });
            resultContext.setGeneratingUrl(oldURL + queryParamBuilder);            
        }
    }

}
