package de.toengi.cili.repository;

import de.toengi.cili.model.entity.BulkImportItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BulkImportItemRepository extends JpaRepository<BulkImportItem, Long> {

    List<BulkImportItem> findByBulkImportJobId(String bulkImportJobId);

    /**
     * Atomic conditional transition to DONE — only affects the row if it is not already DONE.
     * Returns the number of affected rows (0 or 1) so the caller can tell whether IT was the
     * one that completed the item, and only increment the parent job's filesDone counter then.
     * This closes the race where two near-simultaneous completeUpload calls for the same
     * BulkImportItem (e.g. a duplicate/resumed UploadJob) could otherwise both pass a plain
     * read-then-write status check before either commits, double-incrementing filesDone.
     */
    @Modifying
    @Query("UPDATE BulkImportItem i SET i.status = 'DONE', i.resourceId = :resourceId WHERE i.id = :id AND i.status <> 'DONE'")
    int markDoneIfNotAlreadyDone(@Param("id") Long id, @Param("resourceId") Long resourceId);
}
