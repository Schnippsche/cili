package de.toengi.cili.controller.admin;

import de.toengi.cili.config.LogsConfig;
import de.toengi.cili.dto.log.LogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private final LogsConfig logsConfig;

    @GetMapping
    public ResponseEntity<LogResponse> getLogs(
            @RequestParam(defaultValue = "500") int lines) {

        Path logFile = Path.of(logsConfig.getPath()).normalize().resolve("cili.log");
        if (!Files.exists(logFile)) {
            return ResponseEntity.notFound().build();
        }

        try {
            int cap = Math.max(1, Math.min(lines, 2000));
            List<String> tail = tailLines(logFile, cap);
            String lastModified = DateTimeFormatter.ISO_INSTANT.format(
                    Instant.ofEpochMilli(logFile.toFile().lastModified())
                            .atZone(ZoneOffset.UTC));
            return ResponseEntity.ok(new LogResponse(tail, tail.size(), lastModified));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Fehler beim Lesen der Log-Datei", e);
        }
    }

    private List<String> tailLines(Path path, int maxLines) throws IOException {
        ArrayDeque<String> queue = new ArrayDeque<>(maxLines + 1);
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                queue.addLast(line);
                if (queue.size() > maxLines) queue.pollFirst();
            }
        }
        return new ArrayList<>(queue);
    }
}
