package de.toengi.cili.repository;

import de.toengi.cili.model.entity.BulkImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BulkImportJobRepository extends JpaRepository<BulkImportJob, String> {

    @Modifying
    @Query("UPDATE BulkImportJob j SET j.filesDone = j.filesDone + 1 WHERE j.id = :jobId")
    void incrementFilesDone(@Param("jobId") String jobId);

    @Modifying
    @Query("UPDATE BulkImportJob j SET j.filesFailed = j.filesFailed + 1 WHERE j.id = :jobId")
    void incrementFilesFailed(@Param("jobId") String jobId);
}
