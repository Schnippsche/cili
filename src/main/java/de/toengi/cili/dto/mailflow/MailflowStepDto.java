package de.toengi.cili.dto.mailflow;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MailflowStepDto(
    String stepId,
    LocalDate scheduledFor,
    LocalDateTime sentAt,
    String status,
    int attemptCount,
    String lastError
) {}
