package de.toengi.cili.repository;

import de.toengi.cili.model.entity.MailflowStepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MailflowStepStatusRepository extends JpaRepository<MailflowStepStatus, Long> {

    List<MailflowStepStatus> findByInstanceId(Long instanceId);

    Optional<MailflowStepStatus> findByInstanceIdAndStepId(Long instanceId, String stepId);

    @Query("""
        SELECT s FROM MailflowStepStatus s
        WHERE s.status IN (
            'PENDING',
            'ERROR'
        )
          AND s.scheduledFor <= :today
          AND s.attemptCount < 3
        """)
    List<MailflowStepStatus> findDueSteps(@Param("today") LocalDate today);
}
