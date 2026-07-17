package de.toengi.cili.model.entity;

import de.toengi.cili.model.enums.UploadJobStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "upload_jobs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UploadJob implements Persistable<String> {

    @Id
    @Column(length = 36)
    private String id;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() { this.isNew = false; }

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 200)
    private String mimeType;

    @Column(name = "total_size", nullable = false)
    private Long totalSize;

    @Column(name = "chunk_size", nullable = false)
    private Integer chunkSize;

    @Column(name = "chunks_total", nullable = false)
    private Integer chunksTotal;

    @Column(name = "chunks_received", nullable = false)
    @Builder.Default
    private Integer chunksReceived = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UploadJobStatus status = UploadJobStatus.INITIATED;

    @Column(name = "stored_name", length = 36)
    private String storedName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "file_last_modified")
    private Long fileLastModified;

    @Column(name = "bulk_import_item_id")
    private Long bulkImportItemId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
