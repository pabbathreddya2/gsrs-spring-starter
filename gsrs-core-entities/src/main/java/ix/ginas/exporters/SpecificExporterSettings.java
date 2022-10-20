package ix.ginas.exporters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ix.core.models.Text;
import ix.core.util.EntityUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class SpecificExporterSettings {

    private String configurationId;
    private String configurationKey;
    private String exporterKey;
    private JsonNode scrubberSettings;
    private JsonNode exporterSettings;
    private JsonNode generalSettings;//we may not need this item
    private JsonNode expanderSettings;
    private String entityClass;

    public static String getEntityKeyFromClass(String className){
        return "export config " + className;
    }

    public Text asText() {
        Text txt = new Text(getEntityKeyFromClass(entityClass), EntityUtils.EntityWrapper.of(this).toInternalJson());
        return txt;
    }

    public static SpecificExporterSettings fromText(Text text) throws JsonProcessingException {
        SpecificExporterSettings conf = (new ObjectMapper()).readValue(text.getValue(), SpecificExporterSettings.class);
        conf.setConfigurationId(text.id.toString());
        return conf;
    }
}
