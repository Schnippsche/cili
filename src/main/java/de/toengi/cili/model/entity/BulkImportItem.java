package de.toengi.cili.model.entity;

import de.toengi.cili.model.enums.BulkImportItemStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bulk_import_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BulkImportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bulk_import_job_id", nullable = false, length = 36)
    private String bulkImportJobId;

    @Column(name = "relative_path", nullable = false, length = 1000)
    private String relativePath;

    @Column(name = "resolved_folder_id")
    private Long resolvedFolderId;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_last_modified")
    private Long fileLastModified;

    @Column(name = "mime_type", length = 200)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BulkImportItemStatus status;

    @Column(name = "skip_reason", length = 500)
    private String skipReason;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "resource_id")
    private Long resourceId;
}
