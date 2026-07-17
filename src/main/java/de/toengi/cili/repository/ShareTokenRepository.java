package de.toengi.cili.repository;

import de.toengi.cili.model.entity.ShareToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShareTokenRepository extends JpaRepository<ShareToken, Long> {
    Optional<ShareToken> findByToken(String token);
    Optional<ShareToken> findByResourceId(Long resourceId);
}
