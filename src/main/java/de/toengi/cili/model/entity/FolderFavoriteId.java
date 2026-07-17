package de.toengi.cili.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter @Setter @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
public class FolderFavoriteId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "folder_id")
    private Long folderId;
}
