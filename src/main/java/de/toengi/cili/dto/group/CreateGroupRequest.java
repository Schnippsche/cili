package de.toengi.cili.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
    @NotBlank @Size(max = 100) String name,
    String description
) {}
