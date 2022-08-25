package ix.ginas.exporters;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ScrubberExportSettings {
    private List<String> allowedGroups = new ArrayList<>();
    private List<String> prohibitedGroups = new ArrayList<>();
    private boolean onlyPublic;
}
