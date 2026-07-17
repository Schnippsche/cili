package de.toengi.cili.repository;

import de.toengi.cili.model.entity.Folder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    @Query("SELECT f FROM Folder f WHERE f.parentId IS NULL AND f.trashed = false")
    List<Folder> findRootFolders();

    List<Folder> findByParentIdAndTrashedFalse(Long parentId);

    // Returns the folder itself + all descendants (path starts with prefix, e.g. "/1/2/")
    List<Folder> findByPathStartingWith(String pathPrefix);

    List<Folder> findByTrashedTrue();

    List<Folder> findByTrashedFalseOrderByName();

    Page<Folder> findByTrashedFalseOrderByName(Pageable pageable);

    boolean existsByIdInAndPathStartingWith(List<Long> ids, String pathPrefix);

    // Kein Unique-Constraint auf (parent_id, name) in der DB — bei Namensgleichheit
    // wird deterministisch die erste gefundene Zeile verwendet (s. BulkImportService).
    List<Folder> findByParentIdAndName(Long parentId, String name);
}
