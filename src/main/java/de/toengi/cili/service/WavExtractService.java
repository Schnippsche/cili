package de.toengi.cili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.config.FfmpegTranscodeConfig;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WavExtractService {

    private final ResourceRepository resourceRepository;
    private final StorageService storageService;
    private final ProcessingJobService jobService;
    private final CommandRunner commandRunner;
    private final FileStorageConfig storageConfig;
    private final FfmpegTranscodeConfig ffmpegConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Path execute(ProcessingJob job) throws IOException, InterruptedException {
        jobService.markRunning(job, "wav:" + Thread.currentThread().getName());
        try {
            Path wavPath = doExtract(job);
            String resultJson = objectMapper.writeValueAsString(Map.of("wavPath", wavPath.toString()));
            jobService.markDone(job, resultJson);
            return wavPath;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            jobService.markFailed(job, "Interrupted");
            throw ie;
        } catch (Exception e) {
            log.error("WAV extraction failed for job {}: {}", job.getId(), e.getMessage(), e);
            jobService.markFailed(job, e.getMessage());
            throw e;
        }
    }

    private Path doExtract(ProcessingJob job) throws IOException, InterruptedException {
        Resource resource = resourceRepository.findById(job.getResourceId())
            .orElseThrow(() -> new IllegalStateException("Resource not found: " + job.getResourceId()));

        Path remotePath = storageService.resolveLocalPath(resource.getStoredName())
            .orElseThrow(() -> new IllegalStateException("File not found: " + resource.getStoredName()));

        Path tempDir = Paths.get(ffmpegConfig.getTempDir());
        Files.createDirectories(tempDir);

        // Copy to local temp dir first to avoid slow network mount I/O during FFmpeg processing
        Path localInput = tempDir.resolve(UUID.randomUUID().toString());
        Path outputPath = tempDir.resolve(UUID.randomUUID() + ".wav");

        log.info("Copying resource {} from remote to local temp ({} bytes)", job.getResourceId(), Files.size(remotePath));
        long copyStart = System.currentTimeMillis();
        Files.copy(remotePath, localInput);
        log.info("Copy done in {}ms", System.currentTimeMillis() - copyStart);

        try {
            List<String> cmd = buildWavCommand(localInput, outputPath);
            log.info("Extracting WAV for resource {}: {}", job.getResourceId(), cmd);
            int rc = commandRunner.run(cmd);
            if (rc != 0 || !Files.exists(outputPath)) {
                throw new IOException("WAV extraction failed with exit code " + rc);
            }
        } finally {
            Files.deleteIfExists(localInput);
        }
        return outputPath;
    }

    List<String> buildWavCommand(Path input, Path output) {
        return new ArrayList<>(List.of(
            storageConfig.getFfmpegPath(),
            "-y",
            "-threads", "1",
            "-i", input.toString(),
            "-map", "0:a:0",
            "-acodec", "pcm_s16le",
            "-ar", "16000",
            "-ac", "1",
            output.toString()
        ));
    }
}
