package de.toengi.cili.dto.job;

import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;

import java.time.LocalDateTime;

public record ProcessingJobDto(
    Long id,
    Long resourceId,
    ProcessingJobType type,
    ProcessingJobStatus status,
    Integer attempts,
    Integer maxAttempts,
    String errorMessage,
    String result,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ProcessingJobDto from(ProcessingJob j) {
        return new ProcessingJobDto(
            j.getId(), j.getResourceId(), j.getType(), j.getStatus(),
            j.getAttempts(), j.getMaxAttempts(), j.getErrorMessage(),
            j.getResult(),
            j.getStartedAt(), j.getFinishedAt(), j.getCreatedAt(), j.getUpdatedAt()
        );
    }
}
