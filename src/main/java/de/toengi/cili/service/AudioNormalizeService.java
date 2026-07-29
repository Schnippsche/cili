package de.toengi.cili.service;

import de.toengi.cili.config.AudioConfig;
import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.config.FfmpegTranscodeConfig;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import de.toengi.cili.util.FileNameUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioNormalizeService {

    private final ResourceRepository resourceRepository;
    private final StorageService storageService;
    private final ProcessingJobService jobService;
    private final CommandRunner commandRunner;
    private final FileStorageConfig storageConfig;
    private final FfmpegTranscodeConfig ffmpegConfig;
    private final AudioConfig audioConfig;

    public void execute(ProcessingJob job) {
        jobService.markRunning(job, "audio-normalize:" + Thread.currentThread().getName());
        try {
            doNormalize(job);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            jobService.markFailed(job, "Interrupted");
        } catch (Exception e) {
            log.error("Audio normalization failed for job {}: {}", job.getId(), e.getMessage(), e);
            jobService.markFailed(job, e.getMessage());
        }
    }

    private void doNormalize(ProcessingJob job) throws IOException, InterruptedException {
        Resource resource = resourceRepository.findById(job.getResourceId())
            .orElseThrow(() -> new IllegalStateException("Resource not found: " + job.getResourceId()));

        Path inputPath = storageService.resolveLocalPath(resource.getStoredName())
            .orElseThrow(() -> new IllegalStateException("File not found: " + resource.getStoredName()));

        Path tempDir = Paths.get(ffmpegConfig.getTempDir());
        Files.createDirectories(tempDir);

        String newUuid = UUID.randomUUID().toString();
        Path outputPath = tempDir.resolve(newUuid + ".mp3");

        List<String> cmd = buildCommand(inputPath, outputPath);
        log.info("Normalizing audio for resource {}", job.getResourceId());
        int rc = commandRunner.run(cmd);
        if (rc != 0 || !Files.exists(outputPath) || Files.size(outputPath) == 0) {
            Files.deleteIfExists(outputPath);
            throw new IOException("Audio normalization failed with exit code " + rc);
        }

        Path finalPath = storageService.resolveStoragePath(newUuid);
        Files.createDirectories(finalPath.getParent());
        Files.move(outputPath, finalPath, StandardCopyOption.REPLACE_EXISTING);

        String originalStoredName = resource.getStoredName();
        resource.setStoredName(newUuid);
        resource.setSize(Files.size(finalPath));
        resource.setMimeType("audio/mpeg");
        resource.setOriginalName(FileNameUtils.getBaseName(resource.getOriginalName()) + ".mp3");
        resourceRepository.save(resource);

        try {
            storageService.delete(originalStoredName);
        } catch (IOException e) {
            log.warn("Could not delete original audio file {}: {}", originalStoredName, e.getMessage());
        }

        log.info("Audio normalization complete for resource {}: new storedName={}", job.getResourceId(), newUuid);
        jobService.markDone(job, "{\"newStoredName\":\"" + newUuid + "\"}");
    }

    List<String> buildCommand(Path input, Path output) {
        return List.of(
            storageConfig.getFfmpegPath(),
            "-y",
            "-i", input.toString(),
            "-af", audioConfig.getFilterChain(),
            "-codec:a", "libmp3lame",
            "-b:a", audioConfig.getAudioBitrate(),
            output.toString()
        );
    }
}
