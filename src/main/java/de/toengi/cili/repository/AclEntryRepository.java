package de.toengi.cili.repository;

import de.toengi.cili.model.entity.AclEntry;
import de.toengi.cili.model.enums.AclResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AclEntryRepository extends JpaRepository<AclEntry, Long> {

    List<AclEntry> findByResourceTypeAndResourceId(AclResourceType resourceType, Long resourceId);

    List<AclEntry> findBySubjectTypeAndSubjectId(de.toengi.cili.model.enums.AclSubjectType subjectType, Long subjectId);

    @Query("""
        SELECT a FROM AclEntry a
        WHERE a.resourceType = :resourceType AND a.resourceId = :resourceId
          AND (
            (a.subjectType = de.toengi.cili.model.enums.AclSubjectType.USER AND a.subjectId = :userId)
            OR
            (a.subjectType = de.toengi.cili.model.enums.AclSubjectType.GROUP AND a.subjectId IN :groupIds)
          )
        """)
    List<AclEntry> findEffectiveEntries(
        @Param("resourceType") AclResourceType resourceType,
        @Param("resourceId") Long resourceId,
        @Param("userId") Long userId,
        @Param("groupIds") List<Long> groupIds
    );

    @Query("""
        SELECT a FROM AclEntry a
        WHERE a.resourceType = :resourceType AND a.resourceId = :resourceId
          AND a.inheritable = true
          AND (
            (a.subjectType = de.toengi.cili.model.enums.AclSubjectType.USER AND a.subjectId = :userId)
            OR
            (a.subjectType = de.toengi.cili.model.enums.AclSubjectType.GROUP AND a.subjectId IN :groupIds)
          )
        """)
    List<AclEntry> findInheritableEffectiveEntries(
        @Param("resourceType") AclResourceType resourceType,
        @Param("resourceId") Long resourceId,
        @Param("userId") Long userId,
        @Param("groupIds") List<Long> groupIds
    );

    @Modifying
    @Query("DELETE FROM AclEntry a WHERE a.resourceType = :resourceType AND a.resourceId IN :resourceIds")
    void deleteByResourceTypeAndResourceIdIn(
        @Param("resourceType") AclResourceType resourceType,
        @Param("resourceIds") List<Long> resourceIds
    );

    @Query("""
        SELECT a FROM AclEntry a
        WHERE a.resourceType = de.toengi.cili.model.enums.AclResourceType.GLOBAL
          AND a.resourceId IS NULL
          AND (
            (a.subjectType = de.toengi.cili.model.enums.AclSubjectType.USER AND a.subjectId = :userId)
            OR
            (a.subjectType = de.toengi.cili.model.enums.AclSubjectType.GROUP AND a.subjectId IN :groupIds)
          )
        """)
    List<AclEntry> findGlobalEffectiveEntries(
        @Param("userId") Long userId,
        @Param("groupIds") List<Long> groupIds
    );

    @Query("""
        SELECT DISTINCT a.resourceId FROM AclEntry a
        WHERE a.resourceType = de.toengi.cili.model.enums.AclResourceType.FOLDER
          AND a.permission = de.toengi.cili.model.enums.AclPermission.READ
          AND a.grantType = de.toengi.cili.model.enums.AclGrantType.ALLOW
          AND (
            (a.subjectType = de.toengi.cili.model.enums.AclSubjectType.USER AND a.subjectId = :userId)
            OR
            (a.subjectType = de.toengi.cili.model.enums.AclSubjectType.GROUP AND a.subjectId IN :groupIds)
          )
        """)
    List<Long> findAllowedFolderIds(
        @Param("userId") Long userId,
        @Param("groupIds") List<Long> groupIds
    );

    @Query("""
        SELECT DISTINCT a.resourceId FROM AclEntry a
        WHERE a.resourceType = de.toengi.cili.model.enums.AclResourceType.FOLDER
          AND a.permission = de.toengi.cili.model.enums.AclPermission.READ
          AND a.grantType = de.toengi.cili.model.enums.AclGrantType.ALLOW
          AND a.inheritable = true
          AND (
            (a.subjectType = de.toengi.cili.model.enums.AclSubjectType.USER AND a.subjectId = :userId)
            OR
            (a.subjectType = de.toengi.cili.model.enums.AclSubjectType.GROUP AND a.subjectId IN :groupIds)
          )
        """)
    List<Long> findInheritableAllowedFolderIds(
        @Param("userId") Long userId,
        @Param("groupIds") List<Long> groupIds
    );
}
