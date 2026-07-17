package de.toengi.cili.repository;

import de.toengi.cili.model.entity.FolderFavorite;
import de.toengi.cili.model.entity.FolderFavoriteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FolderFavoriteRepository extends JpaRepository<FolderFavorite, FolderFavoriteId> {

    @Query("SELECT f.id.folderId FROM FolderFavorite f WHERE f.id.userId = :userId")
    List<Long> findFolderIdsByUserId(@Param("userId") Long userId);
}
