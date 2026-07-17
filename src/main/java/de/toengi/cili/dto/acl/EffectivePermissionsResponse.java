package de.toengi.cili.dto.acl;

import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.AclResourceType;

import java.util.Set;

public record EffectivePermissionsResponse(
    Long subjectUserId,
    AclResourceType resourceType,
    Long resourceId,
    Set<AclPermission> permissions
) {}
