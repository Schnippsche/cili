package de.toengi.cili.dto.acl;

import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.AclSubjectType;
import jakarta.validation.constraints.NotNull;

public record CreateTestimonialsAclEntryRequest(
    @NotNull AclSubjectType subjectType,
    @NotNull Long subjectId,
    @NotNull AclPermission permission
) {}
