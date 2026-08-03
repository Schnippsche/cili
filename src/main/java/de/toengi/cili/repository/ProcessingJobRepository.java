package de.toengi.cili.repository;

import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    List<ProcessingJob> findByResourceId(Long resourceId);

    Optional<ProcessingJob> findByResourceIdAndType(Long resourceId, ProcessingJobType type);

    List<ProcessingJob> findByStatus(ProcessingJobStatus status);

    List<ProcessingJob> findByStatusIn(List<ProcessingJobStatus> statuses);

    @Query("SELECT j FROM ProcessingJob j WHERE j.status = 'RUNNING' AND j.updatedAt < :cutoff")
    List<ProcessingJob> findZombieJobs(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT j FROM ProcessingJob j WHERE j.type = :type AND j.status = 'PENDING' ORDER BY j.createdAt ASC")
    List<ProcessingJob> findPendingByType(@Param("type") ProcessingJobType type);

    @Query(value = """
        SELECT id FROM processing_jobs
        WHERE type = :type AND status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<Long> findNextClaimableJobId(@Param("type") String type);

    @Modifying
    @Query("UPDATE ProcessingJob j SET j.status = 'PENDING', j.workerLock = NULL, j.startedAt = NULL WHERE j.id IN :ids")
    void resetJobsToPending(@Param("ids") List<Long> ids);

    // Heartbeat für lang laufende externe Prozesse (z.B. Telegram-Import), damit die
    // Zombie-Erkennung (JobRecoveryService) einen aktiv arbeitenden Job nicht fälschlich
    // als hängengeblieben killt. @Modifying-Bulk-Update, damit @UpdateTimestamp (das nur
    // bei Hibernate-Dirty-Checking greift) sicher umgangen und updatedAt garantiert gesetzt wird.
    @Modifying
    @Query("UPDATE ProcessingJob j SET j.updatedAt = CURRENT_TIMESTAMP WHERE j.id = :id")
    void touchUpdatedAt(@Param("id") Long id);

    Page<ProcessingJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ProcessingJob> findByStatusOrderByCreatedAtDesc(ProcessingJobStatus status, Pageable pageable);

    @Modifying
    @Query("DELETE FROM ProcessingJob j WHERE j.status IN :statuses")
    void deleteByStatusIn(@Param("statuses") List<ProcessingJobStatus> statuses);

    List<ProcessingJob> findByResourceIdAndTypeAndCreatedAtAfter(
        Long resourceId, ProcessingJobType type, LocalDateTime since);

    @Query("""
        SELECT j FROM ProcessingJob j
        WHERE j.resourceId = :resourceId
          AND j.type = 'OLLAMA_ANALYSIS'
          AND j.status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')
          AND j.createdAt >= :since
        ORDER BY j.createdAt DESC
        """)
    List<ProcessingJob> findActiveAnalysisJobs(
        @Param("resourceId") Long resourceId,
        @Param("since") LocalDateTime since);

    @Query("""
        SELECT j FROM ProcessingJob j
        WHERE j.resourceId = :resourceId
          AND j.type = 'TESTIMONIAL_SUMMARY'
          AND j.status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')
          AND j.createdAt >= :since
        ORDER BY j.createdAt DESC
        """)
    List<ProcessingJob> findActiveTestimonialSummaryJobs(
        @Param("resourceId") Long resourceId,
        @Param("since") LocalDateTime since);

    @Query("""
        SELECT COUNT(j) > 0 FROM ProcessingJob j
        WHERE j.resourceId = :resourceId
          AND j.type IN ('WAV_EXTRACT', 'WHISPER_TRANSCRIBE')
          AND j.status IN ('PENDING', 'RUNNING')
        """)
    boolean existsActiveTranscriptionJob(@Param("resourceId") Long resourceId);

    boolean existsByTypeAndStatusIn(ProcessingJobType type, List<ProcessingJobStatus> statuses);

    boolean existsByTypeAndSourceAndStatusIn(
        ProcessingJobType type, String source, List<ProcessingJobStatus> statuses);

    @Query("""
        SELECT COUNT(j) > 0 FROM ProcessingJob j
        WHERE j.type IN :types
          AND j.status IN ('PENDING', 'RUNNING')
        """)
    boolean existsActiveJobOfTypes(@Param("types") List<ProcessingJobType> types);

    List<ProcessingJob> findByResourceIdAndTypeAndStatusIn(
        Long resourceId, ProcessingJobType type, List<ProcessingJobStatus> statuses);
}
