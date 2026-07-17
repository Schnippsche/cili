package de.toengi.cili.repository;

import de.toengi.cili.model.entity.CollectionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, Long> {

    List<CollectionItem> findByCollectionId(Long collectionId);

    List<CollectionItem> findByCollectionIdAndResourceIdIsNotNullOrderByAddedAtDesc(Long collectionId);

    List<CollectionItem> findByCollectionIdAndTestimonialIdIsNotNullOrderByAddedAtDesc(Long collectionId);

    Optional<CollectionItem> findByCollectionIdAndResourceId(Long collectionId, Long resourceId);

    Optional<CollectionItem> findByCollectionIdAndTestimonialId(Long collectionId, Long testimonialId);

    long countByCollectionIdAndResourceIdIsNotNull(Long collectionId);

    long countByCollectionIdAndTestimonialIdIsNotNull(Long collectionId);

    boolean existsByCollectionIdAndResourceId(Long collectionId, Long resourceId);
    boolean existsByCollectionIdAndTestimonialId(Long collectionId, Long testimonialId);
}
