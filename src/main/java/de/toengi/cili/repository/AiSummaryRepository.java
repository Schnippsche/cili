package de.toengi.cili.repository;

import de.toengi.cili.model.entity.AiSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiSummaryRepository extends JpaRepository<AiSummary, Long> {
    List<AiSummary> findByResourceId(Long resourceId);
    Optional<AiSummary> findByResourceIdAndLanguageCode(Long resourceId, String languageCode);
}
