package gsrs.holdingArea.model;

import ix.core.models.Backup;
import ix.core.models.Indexable;
import ix.core.models.IndexableRoot;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import javax.persistence.*;

import java.util.UUID;

@Backup
@Table(name = "ix_import_data", indexes={@Index(name="idx_ix_import_data_kind", columnList = "kind"),
        @Index(name="idx_ix_import_data_version", columnList = "version")})
@Slf4j
@Data
@Entity
@IndexableRoot
public class ImportData {

    @Id
    @GenericGenerator(name = "NullUUIDGenerator", strategy = "ix.ginas.models.generators.NullUUIDGenerator")
    @GeneratedValue(generator = "NullUUIDGenerator")
    //maintain backwards compatibility with old GSRS store it as varchar(40) by default hibernate will store uuids as binary
    @Type(type = "uuid-char" )
    @Column(length =40, updatable = false, unique = true)
    @Indexable(name="RecordId")
    private UUID recordId;

    @Indexable(name="Version")
    private int version;

    @Lob
    private String data;

    @Indexable
    @Column(length = 255)
    private String kind;
}
