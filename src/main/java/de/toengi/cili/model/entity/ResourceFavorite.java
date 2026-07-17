package de.toengi.cili.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "resource_favorites")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ResourceFavorite {

    @EmbeddedId
    private ResourceFavoriteId id;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;
}
