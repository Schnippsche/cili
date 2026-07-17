package de.toengi.cili.dto.log;

import java.util.List;

public record LogResponse(
        List<String> lines,
        long totalLines,
        String lastModified
) {}
