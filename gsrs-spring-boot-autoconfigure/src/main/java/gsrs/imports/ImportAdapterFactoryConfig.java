package gsrs.imports;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import gsrs.validator.DefaultValidatorConfig;
import ix.core.util.InheritanceTypeIdResolver;
import ix.ginas.utils.validation.ValidatorPlugin;

import java.util.List;
import java.util.Map;

/*
Information necessary to create an ImportAdapterFactory
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.PROPERTY, property = "configClass", defaultImpl = DefaultImportAdapterFactoryConfig.class)
@JsonTypeIdResolver(InheritanceTypeIdResolver.class)
public interface ImportAdapterFactoryConfig {

    Class getImportAdapterFactoryClass();
    void setImportAdapterFactoryClass(Class importAdapterFactoryClass);

    Map<String, Object> getParameters();
    void setParameters(Map<String, Object> params);

    //optional: class may define its own name
    String getAdapterName();
    void setAdapterName(String name);

    //optional: class may set its own extensions
    List<String> getExtensions();
    void setExtensions(List<String> extensions);

    ImportAdapterFactory newImportAdapterFactory(ObjectMapper mapper, ClassLoader classLoader) throws ClassNotFoundException;
}
