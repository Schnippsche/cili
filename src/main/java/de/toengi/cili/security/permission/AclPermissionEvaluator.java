package de.toengi.cili.security.permission;

import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.AclResourceType;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.AclService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
@RequiredArgsConstructor
public class AclPermissionEvaluator implements PermissionEvaluator {

    private final AclService aclService;

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId,
                                  String targetType, Object permission) {
        if (!(auth.getPrincipal() instanceof CiliUserDetails userDetails)) return false;
        if (!(permission instanceof String permStr)) return false;

        AclPermission aclPermission;
        try {
            aclPermission = AclPermission.valueOf(permStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }

        AclResourceType resourceType;
        try {
            resourceType = AclResourceType.valueOf(targetType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }

        Long resourceId = targetId instanceof Long l ? l : null;
        return aclService.hasPermission(userDetails.getUserId(), resourceId, resourceType, aclPermission);
    }
}
