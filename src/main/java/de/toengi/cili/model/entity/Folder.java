package de.toengi.cili.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "folders")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 1000)
    @Builder.Default
    private String path = "/";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_trashed", nullable = false)
    @Builder.Default
    private boolean trashed = false;

    @Column(name = "trashed_at")
    private LocalDateTime trashedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
