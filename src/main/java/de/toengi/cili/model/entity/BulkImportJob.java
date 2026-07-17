package de.toengi.cili.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "bulk_import_jobs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BulkImportJob implements Persistable<String> {

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

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "target_folder_id", nullable = false)
    private Long targetFolderId;

    @Column(name = "root_name", nullable = false, length = 255)
    private String rootName;

    @Column(name = "files_total", nullable = false)
    @Builder.Default
    private Integer filesTotal = 0;

    @Column(name = "files_done", nullable = false)
    @Builder.Default
    private Integer filesDone = 0;

    @Column(name = "files_skipped", nullable = false)
    @Builder.Default
    private Integer filesSkipped = 0;

    @Column(name = "files_failed", nullable = false)
    @Builder.Default
    private Integer filesFailed = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
