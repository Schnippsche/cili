package de.toengi.cili.dto.group;

import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(
    @Size(max = 100) String name,
    String description
) {}
