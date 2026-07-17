package de.toengi.cili.model.entity;

import de.toengi.cili.model.enums.StorageType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "resources")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "testimonial_id")
    private Long testimonialId;

    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 36, unique = true)
    private String storedName;

    @Column(name = "mime_type", nullable = false, length = 200)
    private String mimeType;

    @Column(nullable = false)
    @Builder.Default
    private Long size = 0L;

    @Column(length = 64)
    private String checksum;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false)
    @Builder.Default
    private StorageType storageType = StorageType.LOCAL;

    @Column(name = "file_date")
    private LocalDateTime fileDate;

    @Column(name = "sort_order")
    private Long sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
