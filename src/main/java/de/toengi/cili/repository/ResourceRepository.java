package de.toengi.cili.repository;

import de.toengi.cili.model.entity.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, Long> {

    String RESOURCE_COLUMNS =
            "r.id, r.folder_id, r.testimonial_id, r.original_name, r.stored_name, r.mime_type, " +
            "r.size, r.checksum, r.uploader_id, r.storage_type, r.file_date, r.sort_order, r.created_at, r.updated_at";

    @Query("SELECT r FROM Resource r WHERE r.folderId = :folderId " +
           "ORDER BY CASE WHEN r.sortOrder IS NULL THEN 1 ELSE 0 END, r.sortOrder ASC, r.createdAt ASC")
    List<Resource> findByFolderId(@Param("folderId") Long folderId);

    List<Resource> findByTestimonialIdOrderByCreatedAtAsc(Long testimonialId);

    @Query("SELECT r FROM Resource r WHERE r.folderId IS NOT NULL")
    Page<Resource> findAllInFolders(Pageable pageable);

    Page<Resource> findByFolderId(Long folderId, Pageable pageable);

    @Query(value =
           "SELECT " + RESOURCE_COLUMNS + " FROM resources r " +
           "WHERE r.folder_id IN :folderIds AND MATCH(r.original_name) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT " + RESOURCE_COLUMNS + " FROM resources r JOIN resource_metadata rm ON rm.resource_id = r.id " +
           "WHERE r.folder_id IN :folderIds " +
           "AND MATCH(rm.title, rm.description, rm.tags, rm.categories) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT " + RESOURCE_COLUMNS + " FROM resources r JOIN resource_metadata rm ON rm.resource_id = r.id " +
           "WHERE r.folder_id IN :folderIds AND MATCH(rm.text_content) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT " + RESOURCE_COLUMNS + " FROM resources r JOIN subtitle_tracks st ON st.resource_id = r.id " +
           "WHERE r.folder_id IN :folderIds AND MATCH(st.text_content) AGAINST (:boolQ IN BOOLEAN MODE)",
           countQuery =
           "SELECT COUNT(*) FROM (" +
           "SELECT r.id FROM resources r " +
           "WHERE r.folder_id IN :folderIds AND MATCH(r.original_name) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT r.id FROM resources r JOIN resource_metadata rm ON rm.resource_id = r.id " +
           "WHERE r.folder_id IN :folderIds " +
           "AND MATCH(rm.title, rm.description, rm.tags, rm.categories) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT r.id FROM resources r JOIN resource_metadata rm ON rm.resource_id = r.id " +
           "WHERE r.folder_id IN :folderIds AND MATCH(rm.text_content) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT r.id FROM resources r JOIN subtitle_tracks st ON st.resource_id = r.id " +
           "WHERE r.folder_id IN :folderIds AND MATCH(st.text_content) AGAINST (:boolQ IN BOOLEAN MODE)) x",
           nativeQuery = true)
    Page<Resource> searchByNameOrMetadataAndFolderIn(
        @Param("folderIds") List<Long> folderIds,
        @Param("boolQ") String boolQ,
        Pageable pageable);

    Page<Resource> findByFolderIdIn(List<Long> folderIds, Pageable pageable);

    @Query(value =
           "SELECT " + RESOURCE_COLUMNS + " FROM resources r " +
           "WHERE r.folder_id IS NOT NULL AND MATCH(r.original_name) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT " + RESOURCE_COLUMNS + " FROM resources r JOIN resource_metadata rm ON rm.resource_id = r.id " +
           "WHERE r.folder_id IS NOT NULL " +
           "AND MATCH(rm.title, rm.description, rm.tags, rm.categories) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT " + RESOURCE_COLUMNS + " FROM resources r JOIN resource_metadata rm ON rm.resource_id = r.id " +
           "WHERE r.folder_id IS NOT NULL AND MATCH(rm.text_content) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT " + RESOURCE_COLUMNS + " FROM resources r JOIN subtitle_tracks st ON st.resource_id = r.id " +
           "WHERE r.folder_id IS NOT NULL AND MATCH(st.text_content) AGAINST (:boolQ IN BOOLEAN MODE)",
           countQuery =
           "SELECT COUNT(*) FROM (" +
           "SELECT r.id FROM resources r " +
           "WHERE r.folder_id IS NOT NULL AND MATCH(r.original_name) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT r.id FROM resources r JOIN resource_metadata rm ON rm.resource_id = r.id " +
           "WHERE r.folder_id IS NOT NULL " +
           "AND MATCH(rm.title, rm.description, rm.tags, rm.categories) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT r.id FROM resources r JOIN resource_metadata rm ON rm.resource_id = r.id " +
           "WHERE r.folder_id IS NOT NULL AND MATCH(rm.text_content) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT r.id FROM resources r JOIN subtitle_tracks st ON st.resource_id = r.id " +
           "WHERE r.folder_id IS NOT NULL AND MATCH(st.text_content) AGAINST (:boolQ IN BOOLEAN MODE)) x",
           nativeQuery = true)
    Page<Resource> searchByNameOrMetadata(@Param("boolQ") String boolQ, Pageable pageable);

    @Query(value =
           "SELECT " + RESOURCE_COLUMNS + " FROM resources r " +
           "WHERE r.folder_id = :folderId AND MATCH(r.original_name) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT " + RESOURCE_COLUMNS + " FROM resources r JOIN resource_metadata rm ON rm.resource_id = r.id " +
           "WHERE r.folder_id = :folderId " +
           "AND MATCH(rm.title, rm.description, rm.tags, rm.categories) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT " + RESOURCE_COLUMNS + " FROM resources r JOIN resource_metadata rm ON rm.resource_id = r.id " +
           "WHERE r.folder_id = :folderId AND MATCH(rm.text_content) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT " + RESOURCE_COLUMNS + " FROM resources r JOIN subtitle_tracks st ON st.resource_id = r.id " +
           "WHERE r.folder_id = :folderId AND MATCH(st.text_content) AGAINST (:boolQ IN BOOLEAN MODE)",
           countQuery =
           "SELECT COUNT(*) FROM (" +
           "SELECT r.id FROM resources r " +
           "WHERE r.folder_id = :folderId AND MATCH(r.original_name) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT r.id FROM resources r JOIN resource_metadata rm ON rm.resource_id = r.id " +
           "WHERE r.folder_id = :folderId " +
           "AND MATCH(rm.title, rm.description, rm.tags, rm.categories) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT r.id FROM resources r JOIN resource_metadata rm ON rm.resource_id = r.id " +
           "WHERE r.folder_id = :folderId AND MATCH(rm.text_content) AGAINST (:boolQ IN BOOLEAN MODE) " +
           "UNION " +
           "SELECT r.id FROM resources r JOIN subtitle_tracks st ON st.resource_id = r.id " +
           "WHERE r.folder_id = :folderId AND MATCH(st.text_content) AGAINST (:boolQ IN BOOLEAN MODE)) x",
           nativeQuery = true)
    Page<Resource> searchByFolderAndNameOrMetadata(@Param("folderId") Long folderId, @Param("boolQ") String boolQ, Pageable pageable);

    @Modifying
    @Query("UPDATE Resource r SET r.sortOrder = r.sortOrder + 1 WHERE r.folderId = :folderId AND r.sortOrder IS NOT NULL")
    void incrementSortOrderByFolderId(@Param("folderId") Long folderId);

    @Query("SELECT r.mimeType, COUNT(r) FROM Resource r " +
           "WHERE r.originalName LIKE CONCAT('%', :q, '%') GROUP BY r.mimeType")
    List<Object[]> countByMimeType(@Param("q") String q);

    @Query("SELECT rm.language, COUNT(r) FROM Resource r " +
           "LEFT JOIN ResourceMetadata rm ON rm.resourceId = r.id " +
           "WHERE r.originalName LIKE CONCAT('%', :q, '%') AND rm.language IS NOT NULL " +
           "GROUP BY rm.language")
    List<Object[]> countByLanguage(@Param("q") String q);

    @Query("SELECT r FROM Resource r WHERE r.folderId = :folderId AND r.originalName LIKE :prefix AND r.mimeType LIKE :mimePrefix")
    List<Resource> findInFolderByNamePrefix(
        @Param("folderId") Long folderId,
        @Param("prefix") String prefix,
        @Param("mimePrefix") String mimePrefix);
}
