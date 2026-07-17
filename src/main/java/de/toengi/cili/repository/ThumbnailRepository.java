package de.toengi.cili.repository;

import de.toengi.cili.model.entity.Thumbnail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ThumbnailRepository extends JpaRepository<Thumbnail, Long> {
    Optional<Thumbnail> findByResourceId(Long resourceId);
    List<Thumbnail> findByResourceIdIn(Collection<Long> resourceIds);
}
