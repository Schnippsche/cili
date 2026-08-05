package de.toengi.cili.dto.mailflow;

import java.time.LocalDateTime;
import java.util.List;

public record MailflowInstanceDto(
    Long id,
    String flowName,
    String description,
    LocalDateTime startedAt,
    String status,
    List<MailflowStepDto> steps
) {}
