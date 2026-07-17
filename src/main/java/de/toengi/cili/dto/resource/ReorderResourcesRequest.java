package de.toengi.cili.dto.resource;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReorderResourcesRequest(@NotNull List<Long> resourceIds) {}
