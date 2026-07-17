package de.toengi.cili.repository;

import de.toengi.cili.model.entity.ResourceFavorite;
import de.toengi.cili.model.entity.ResourceFavoriteId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceFavoriteRepository extends JpaRepository<ResourceFavorite, ResourceFavoriteId> {
    List<ResourceFavorite> findByIdUserId(Long userId);
}
