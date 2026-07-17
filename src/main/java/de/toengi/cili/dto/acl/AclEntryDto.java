package de.toengi.cili.dto.acl;

import de.toengi.cili.model.enums.*;

import java.time.LocalDateTime;

public record AclEntryDto(
    Long id,
    AclSubjectType subjectType,
    Long subjectId,
    AclResourceType resourceType,
    Long resourceId,
    AclPermission permission,
    AclGrantType grantType,
    boolean inheritable,
    LocalDateTime createdAt
) {}
