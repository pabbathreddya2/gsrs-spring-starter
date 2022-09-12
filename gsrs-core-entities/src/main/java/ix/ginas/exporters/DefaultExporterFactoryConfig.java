package ix.ginas.exporters;

import com.fasterxml.jackson.databind.JsonNode;
import ix.core.models.Text;
import ix.core.util.EntityUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/*
Default implementation
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class DefaultExporterFactoryConfig {

    private String configurationId;
    private String configurationKey;
    private String exporterKey;
    private JsonNode scrubberSettings;
    private JsonNode exporterSettings;
    private JsonNode generalSettings;
    private String entityClass;

    public static String getEntityKeyFromClass(String className){
        return "export config " + className;
    }

    public Text asText() {
        Text txt = new Text(getEntityKeyFromClass(entityClass), EntityUtils.EntityWrapper.of(this).toInternalJson());
        return txt;
    }
}
