package de.toengi.cili.repository;

import de.toengi.cili.model.entity.CollectionShareToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CollectionShareTokenRepository extends JpaRepository<CollectionShareToken, Long> {
    Optional<CollectionShareToken> findByToken(String token);
    Optional<CollectionShareToken> findByCollectionId(Long collectionId);
}
