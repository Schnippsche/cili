package de.toengi.cili.repository;

import de.toengi.cili.model.entity.GpuMetricSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GpuMetricSampleRepository extends JpaRepository<GpuMetricSample, Long> {

    Optional<GpuMetricSample> findTopByOrderBySampledAtDesc();

    List<GpuMetricSample> findBySampledAtAfterOrderBySampledAtAsc(LocalDateTime since);

    @Modifying
    @Query("DELETE FROM GpuMetricSample s WHERE s.sampledAt < :cutoff")
    long deleteBySampledAtBefore(LocalDateTime cutoff);
}
