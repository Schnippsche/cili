package de.toengi.cili.repository;

import de.toengi.cili.model.entity.SubtitleTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubtitleTrackRepository extends JpaRepository<SubtitleTrack, Long> {
    List<SubtitleTrack> findByResourceId(Long resourceId);
    boolean existsByResourceIdAndLanguageCode(Long resourceId, String languageCode);
    Optional<SubtitleTrack> findByIdAndResourceId(Long id, Long resourceId);
    Optional<SubtitleTrack> findByResourceIdAndLanguageCode(Long resourceId, String languageCode);

    @Query("SELECT st FROM SubtitleTrack st WHERE st.resourceId IN :ids AND st.textContent IS NOT NULL AND st.textContent <> ''")
    List<SubtitleTrack> findWithTextContentByResourceIdIn(@Param("ids") List<Long> ids);

    @Query("SELECT COUNT(st) > 0 FROM SubtitleTrack st WHERE st.resourceId = :resourceId AND st.textContent IS NOT NULL AND st.textContent <> ''")
    boolean existsWithTextContentByResourceId(@Param("resourceId") Long resourceId);

    @Query(value = """
        SELECT st.resource_id FROM subtitle_tracks st
        WHERE st.language_code = :lang
          AND st.text_content IS NOT NULL AND st.text_content <> ''
          AND NOT EXISTS (
              SELECT 1 FROM ai_summaries s
              WHERE s.resource_id = st.resource_id AND s.language_code = :lang
          )
        ORDER BY st.resource_id ASC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Long> findOldestResourceMissingSummary(@Param("lang") String languageCode);

    List<SubtitleTrack> findByResourceIdIn(List<Long> resourceIds);
}
