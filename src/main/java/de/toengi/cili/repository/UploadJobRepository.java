package de.toengi.cili.repository;

import de.toengi.cili.model.entity.UploadJob;
import de.toengi.cili.model.enums.UploadJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface UploadJobRepository extends JpaRepository<UploadJob, String> {

    List<UploadJob> findByExpiresAtBeforeAndStatusNot(LocalDateTime cutoff, UploadJobStatus status);

    /** IDs aller noch aktiven Jobs (nicht abgelaufen ODER bereits abgeschlossen). */
    @Query("SELECT j.id FROM UploadJob j WHERE j.expiresAt > :cutoff OR j.status = :status")
    Set<String> findActiveJobIds(@Param("cutoff") LocalDateTime cutoff, @Param("status") UploadJobStatus status);
}
