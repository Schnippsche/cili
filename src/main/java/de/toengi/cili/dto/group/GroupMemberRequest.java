package de.toengi.cili.dto.group;

import jakarta.validation.constraints.NotNull;

public record GroupMemberRequest(@NotNull Long userId) {}
