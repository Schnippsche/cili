package de.toengi.cili.dto.upload;

import de.toengi.cili.model.enums.UploadJobStatus;

public record UploadJobDto(
        String jobId,
        int chunksTotal,
        int chunksReceived,
        UploadJobStatus status
) {}
