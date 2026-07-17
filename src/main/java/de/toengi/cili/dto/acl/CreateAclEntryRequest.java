package de.toengi.cili.dto.acl;

import de.toengi.cili.model.enums.*;
import jakarta.validation.constraints.NotNull;

public record CreateAclEntryRequest(
    @NotNull AclSubjectType subjectType,
    @NotNull Long subjectId,
    @NotNull AclPermission permission,
    @NotNull AclGrantType grantType,
    boolean inheritable
) {}
