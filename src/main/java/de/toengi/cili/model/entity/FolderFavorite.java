package de.toengi.cili.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "folder_favorites")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FolderFavorite {

    @EmbeddedId
    private FolderFavoriteId id;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;
}
