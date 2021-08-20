package gsrs;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import gsrs.controller.BuildInfoController;
import gsrs.controller.ExportController;
import gsrs.controller.GsrsWebConfig;
import gsrs.controller.HealthController;
import gsrs.controller.UserController;
import gsrs.entityProcessor.BasicEntityProcessorConfiguration;
import gsrs.entityProcessor.ConfigBasedEntityProcessorConfiguration;
import gsrs.events.listeners.ReindexEventListener;
import gsrs.indexer.ComponentScanIndexValueMakerConfiguration;
import gsrs.indexer.ConfigBasedIndexValueMakerConfiguration;
import gsrs.search.SearchResultController;
import gsrs.springUtils.StaticContextAccessor;
import gsrs.validator.ConfigBasedValidatorFactoryConfiguration;
import gsrs.validator.ValidatorConfigConverter;
import ix.core.search.text.Lucene4IndexServiceFactory;
import ix.core.search.text.TextIndexerConfig;
import ix.core.search.text.TextIndexerEntityListener;
import ix.core.search.text.TextIndexerSingletonConfiguration;
import ix.core.util.pojopointer.LambdaParseRegistry;
import ix.core.util.pojopointer.URIPojoPointerParser;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GsrsApiSelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata annotationMetadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(annotationMetadata.getAnnotationAttributes(EnableGsrsApi.class.getName(), false));
        EnableGsrsApi.IndexerType indexerType = attributes.getEnum("indexerType");

        List<Class> componentsToInclude = new ArrayList<>();
        
        componentsToInclude.add(DefaultDataSourceConfig.class);
        componentsToInclude.add(GsrsWebConfig.class);
        componentsToInclude.add(StaticContextAccessor.class);
        componentsToInclude.add(ReindexEventListener.class);
        componentsToInclude.add(BuildInfoController.class);
        componentsToInclude.add(UserController.class);
        componentsToInclude.add(HealthController.class);
        switch(indexerType){
            case LEGACY: {
                componentsToInclude.add(SpecialFieldsProperties.class);
                componentsToInclude.add(TextIndexerSingletonConfiguration.class);
                componentsToInclude.add(TextIndexerConfig.class);
                componentsToInclude.add(TextIndexerEntityListener.class);
                componentsToInclude.add(Lucene4IndexServiceFactory.class);

            }
        }
        EnableGsrsApi.IndexValueMakerDetector indexValueMakerDetector = attributes.getEnum("indexValueMakerDetector");
        switch (indexValueMakerDetector){
            case CONF:
                componentsToInclude.add(ConfigBasedIndexValueMakerConfiguration.class);
                break;
            case COMPONENT_SCAN:
                componentsToInclude.add(ComponentScanIndexValueMakerConfiguration.class);
                break;
            default: break;
        }

        EnableGsrsApi.EntityProcessorDetector entityProcessorDetector = attributes.getEnum("entityProcessorDetector");


        switch(entityProcessorDetector){
            case COMPONENT_SCAN:
                componentsToInclude.add(BasicEntityProcessorConfiguration.class);
                break;
            case CONF:
                componentsToInclude.add(ConfigBasedEntityProcessorConfiguration.class);
                break;
            default: break;
        }

        //TODO make something other than CONF based validator?
        componentsToInclude.add(ValidatorConfigConverter.class);
        componentsToInclude.add(ConfigBasedValidatorFactoryConfiguration.class);
        componentsToInclude.add(URIPojoPointerParser.class);
        componentsToInclude.add(LambdaParseRegistry.class);
        componentsToInclude.add(RegisteredFunctionProperties.class);
        componentsToInclude.add(ExportController.class);
        componentsToInclude.add(SearchResultController.class);
        
        return componentsToInclude.stream().map(Class::getName)
                .peek(c-> log.debug("including:" + c))
                .toArray(i-> new String[i]);
    }
}
