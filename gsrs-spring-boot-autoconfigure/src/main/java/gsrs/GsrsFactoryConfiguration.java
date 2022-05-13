package gsrs;

import akka.event.Logging;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gsrs.controller.AbstractImportSupportingGsrsEntityController;
import gsrs.entityProcessor.EntityProcessorConfig;
import gsrs.imports.ImportAdapterFactoryConfig;
import gsrs.validator.ValidatorConfig;
import gsrs.validator.ValidatorConfigList;
import ix.core.util.EntityUtils;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties("gsrs")
@Data
@Slf4j
public class GsrsFactoryConfiguration {

    private Map<String, List<Map<String,Object>>> validators;
    private Map<String, List<Map<String,Object>>> importAdapterFactories;
    private List<EntityProcessorConfig> entityProcessors;

    private boolean createUnknownUsers= false;

    public List<EntityProcessorConfig> getEntityProcessors(){
        if(entityProcessors ==null){
            //nothing set
            return Collections.emptyList();
        }
        return new ArrayList<>(entityProcessors);
    }



    public List<? extends ValidatorConfig> getValidatorConfigByContext(String context){
        if(validators ==null){
            //nothing set
            return Collections.emptyList();
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            List<Map<String,Object>> list = validators.get(context);

            if(list==null || list.isEmpty()){
                return Collections.emptyList();
            }
            List<? extends ValidatorConfig> configs = mapper.convertValue(list, new TypeReference<List<? extends ValidatorConfig>>() {});

//            List<ValidatorConfig> configs = new ArrayList<>();
//            for (Map<String,Object> n : list) {
//
//                Class<? extends ValidatorConfig> configClass = (Class<? extends ValidatorConfig>) n.get("configClass");
//                if(configClass ==null) {
//                    configs.add(mapper.convertValue(n, ValidatorConfig.class));
//                }else{
//                    configs.add(mapper.convertValue(n, configClass));
//                }
//            }
            return configs;
        }catch(Throwable t){
            throw t;
        }
//        ValidatorConfigList list = (ValidatorConfigList) validators.get(context);
//        if(list ==null){
//            return Collections.emptyList();
//        }
//        return list.getConfigList();
    }

    /*
    retrieve a set of configuration items for the creation of AdapterFactory/ies based on
    context -- the name of a type of entity that the Adapters will create.
     */
    public List<? extends ImportAdapterFactoryConfig> getImportAdapterFactories(String context) {
        log.trace("starting in getImportAdapterFactories");
        if(importAdapterFactories ==null) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> list = importAdapterFactories.get(context);
            log.trace("list:");
            list.forEach(i->i.keySet().forEach(k->log.trace("key: %s; value: %s", k, i.get(k))));

            if(list==null || list.isEmpty()){
                log.warn("no import adapter factory configuration info found!");
                return Collections.emptyList();
            }
            List<? extends ImportAdapterFactoryConfig> configs = EntityUtils.convertClean(list, new TypeReference<List<? extends ImportAdapterFactoryConfig>>() {});
            return configs;
        }
        catch (Exception t){
            log.error("Error fetching import factory config");
            throw t;
        }

    }
}
