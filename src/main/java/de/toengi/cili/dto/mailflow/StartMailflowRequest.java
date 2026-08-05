package de.toengi.cili.dto.mailflow;

import jakarta.validation.constraints.NotBlank;

public record StartMailflowRequest(@NotBlank String flowName) {}
