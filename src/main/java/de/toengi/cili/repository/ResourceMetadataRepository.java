package de.toengi.cili.repository;

import de.toengi.cili.model.entity.ResourceMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResourceMetadataRepository extends JpaRepository<ResourceMetadata, Long> {
    Optional<ResourceMetadata> findByResourceId(Long resourceId);
    List<ResourceMetadata> findByResourceIdIn(Collection<Long> resourceIds);
}
