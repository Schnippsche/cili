package de.toengi.cili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.FfmpegTranscodeConfig;
import de.toengi.cili.config.FileStorageConfig;
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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoTranscodeService {

    private final ResourceRepository resourceRepository;
    private final StorageService storageService;
    private final ProcessingJobService jobService;
    private final CommandRunner commandRunner;
    private final FfmpegTranscodeConfig ffmpegConfig;
    private final FileStorageConfig storageConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void execute(ProcessingJob job) {
        jobService.markRunning(job, "transcode:" + Thread.currentThread().getName());
        try {
            doTranscode(job);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            jobService.markFailed(job, "Interrupted");
        } catch (Exception e) {
            log.error("Transcode failed for job {}: {}", job.getId(), e.getMessage(), e);
            jobService.markFailed(job, e.getMessage());
        }
    }

    private void doTranscode(ProcessingJob job) throws IOException, InterruptedException {
        Resource resource = resourceRepository.findById(job.getResourceId())
                .orElseThrow(() -> new IllegalStateException("Resource not found: " + job.getResourceId()));

        Path inputPath = storageService.resolveLocalPath(resource.getStoredName())
                .orElseThrow(() -> new IllegalStateException("File not found: " + resource.getStoredName()));

        Path tempDir = Paths.get(ffmpegConfig.getTempDir());
        Files.createDirectories(tempDir);

        String newUuid = UUID.randomUUID().toString();
        Path outputPath = tempDir.resolve(newUuid + ".mp4");

        List<String> cmd = buildFfmpegCommand(inputPath, outputPath, ffmpegConfig);
        log.info("Starting transcode for resource {}: {}", job.getResourceId(), cmd);

        int rc = commandRunner.run(cmd);
        if (rc != 0 || !Files.exists(outputPath) || Files.size(outputPath) == 0) {
            Files.deleteIfExists(outputPath);
            throw new IOException("FFmpeg transcode failed with exit code " + rc);
        }

        Path finalPath = storageService.resolveStoragePath(newUuid);
        Files.createDirectories(finalPath.getParent());
        Files.move(outputPath, finalPath, StandardCopyOption.REPLACE_EXISTING);

        String originalStoredName = resource.getStoredName();
        resource.setStoredName(newUuid);
        resource.setSize(Files.size(finalPath));
        resourceRepository.save(resource);

        try {
            storageService.delete(originalStoredName);
        } catch (IOException e) {
            log.warn("Could not delete original file {}: {}", originalStoredName, e.getMessage());
        }

        log.info("Transcode complete for resource {}: new storedName={}",
                job.getResourceId(), newUuid);

        String resultJson = objectMapper.writeValueAsString(
                Map.of("newStoredName", newUuid, "originalStoredName", originalStoredName));
        jobService.markDone(job, resultJson);
    }

    List<String> buildFfmpegCommand(Path input, Path output, FfmpegTranscodeConfig cfg) {
        boolean nvenc = cfg.getVideoCodec().contains("nvenc");
        // NVENC uses -cq (constant quality); libx264 uses -crf
        return new ArrayList<>(List.of(
                storageConfig.getFfmpegPath(),
                "-y",
                "-i", input.toString(),
                "-vcodec", cfg.getVideoCodec(),
                "-acodec", cfg.getAudioCodec(),
                "-profile:a", "aac_low",
                // NVENC uses -cq (constant quality); libx264 uses -crf
                nvenc ? "-cq" : "-crf", String.valueOf(cfg.getCrf()),
                "-preset", cfg.getPreset(),
                "-maxrate", cfg.getMaxBitrate(),
                "-bufsize", "8000k",
                "-movflags", "+faststart",
                "-vf", "scale=-2:" + cfg.getTargetHeight(),
                output.toString()
        ));
    }
}
