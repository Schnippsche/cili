package de.toengi.cili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.FfmpegTranscodeConfig;
import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.event.ResourceUploadedEvent;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.ResourceMetadata;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.AclResourceType;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.model.enums.StorageType;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import de.toengi.cili.util.FileNameUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class VideoClipService {

    private final ResourceRepository resourceRepository;
    private final ResourceMetadataRepository metadataRepository;
    private final StorageService storageService;
    private final ProcessingJobService jobService;
    private final CommandRunner commandRunner;
    private final FfmpegTranscodeConfig ffmpegConfig;
    private final FileStorageConfig storageConfig;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager txManager;
    private final AclService aclService;
    private final ProcessingJobRepository jobRepository;
    private final Executor transcodeExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<ProcessingJobStatus> ACTIVE =
            List.of(ProcessingJobStatus.PENDING, ProcessingJobStatus.RUNNING);

    public VideoClipService(
            ResourceRepository resourceRepository,
            ResourceMetadataRepository metadataRepository,
            StorageService storageService,
            ProcessingJobService jobService,
            CommandRunner commandRunner,
            FfmpegTranscodeConfig ffmpegConfig,
            FileStorageConfig storageConfig,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager txManager,
            AclService aclService,
            ProcessingJobRepository jobRepository,
            @Qualifier("transcodeExecutor") Executor transcodeExecutor) {
        this.resourceRepository = resourceRepository;
        this.metadataRepository = metadataRepository;
        this.storageService = storageService;
        this.jobService = jobService;
        this.commandRunner = commandRunner;
        this.ffmpegConfig = ffmpegConfig;
        this.storageConfig = storageConfig;
        this.eventPublisher = eventPublisher;
        this.txManager = txManager;
        this.aclService = aclService;
        this.jobRepository = jobRepository;
        this.transcodeExecutor = transcodeExecutor;
    }

    /** Stellt einen Clip-Job in die Warteschlange und gibt die Job-ID zurück. */
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public long enqueueClip(Long resourceId, long startMs, long endMs, String title, Long actorUserId) {
        if (endMs <= startMs) {
            throw new IllegalArgumentException("endMs muss größer als startMs sein");
        }
        String trimmedTitle = title == null ? null : title.trim();
        if (trimmedTitle != null && trimmedTitle.length() > 500) {
            throw new IllegalArgumentException("Titel darf maximal 500 Zeichen lang sein");
        }
        Resource source = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", resourceId));
        boolean canUpload = aclService.hasPermission(actorUserId, source.getFolderId(),
                AclResourceType.FOLDER, AclPermission.UPLOAD);
        if (!canUpload) {
            throw new AccessDeniedException(
                    "Kein Upload-Recht im Zielordner " + source.getFolderId());
        }

        Optional<ProcessingJob> dup = findActiveDuplicate(resourceId, startMs, endMs);
        if (dup.isPresent()) {
            log.info("VIDEO_CLIP für resource {} (startMs={}, endMs={}) bereits aktiv — Job {} zurückgegeben",
                    resourceId, startMs, endMs, dup.get().getId());
            return dup.get().getId();
        }

        String params = toJson(Map.of("startMs", startMs, "endMs", endMs, "title", title == null ? "" : title));
        ProcessingJob job = jobService.createJob(resourceId, ProcessingJobType.VIDEO_CLIP, null, params);
        transcodeExecutor.execute(() -> execute(job));
        return job.getId();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public List<ProcessingJob> getActiveClipJobs(Long resourceId) {
        return jobRepository.findByResourceIdAndTypeAndStatusIn(
                resourceId, ProcessingJobType.VIDEO_CLIP, ACTIVE);
    }

    private Optional<ProcessingJob> findActiveDuplicate(Long resourceId, long startMs, long endMs) {
        return jobRepository.findByResourceIdAndTypeAndStatusIn(
                        resourceId, ProcessingJobType.VIDEO_CLIP, ACTIVE)
                .stream()
                .filter(j -> Long.valueOf(startMs).equals(parseLongParam(j, "startMs"))
                        && Long.valueOf(endMs).equals(parseLongParam(j, "endMs")))
                .findFirst();
    }

    private Long parseLongParam(ProcessingJob job, String key) {
        try {
            if (job.getResult() == null) return null;
            var node = objectMapper.readTree(job.getResult()).path(key);
            return node.isMissingNode() || node.isNull() ? null : node.asLong();
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    public void execute(ProcessingJob job) {
        jobService.markRunning(job, "clip:" + Thread.currentThread().getName());
        try {
            doClip(job);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            jobService.markFailed(job, "Interrupted");
        } catch (Exception e) {
            log.error("Clip-Erstellung fehlgeschlagen für job {}: {}", job.getId(), e.getMessage(), e);
            jobService.markFailed(job, e.getMessage());
        }
    }

    private void doClip(ProcessingJob job) throws IOException, InterruptedException {
        long startMs = readLongParam(job, "startMs");
        long endMs = readLongParam(job, "endMs");
        if (endMs <= startMs) {
            throw new IllegalStateException(
                    "endMs muss größer als startMs sein: startMs=" + startMs + ", endMs=" + endMs);
        }

        Resource source = resourceRepository.findById(job.getResourceId())
                .orElseThrow(() -> new IllegalStateException("Resource not found: " + job.getResourceId()));

        Path inputPath = storageService.resolveLocalPath(source.getStoredName())
                .orElseThrow(() -> new IllegalStateException("File not found: " + source.getStoredName()));

        Path tempDir = Paths.get(ffmpegConfig.getTempDir());
        Files.createDirectories(tempDir);

        String newUuid = UUID.randomUUID().toString();
        Path outputPath = tempDir.resolve(newUuid + ".mp4");

        List<String> cmd = buildClipCommand(inputPath, outputPath, startMs, endMs, ffmpegConfig);
        log.info("Erstelle Clip für resource {}: {}", job.getResourceId(), cmd);

        int rc = commandRunner.run(cmd);
        if (rc != 0 || !Files.exists(outputPath) || Files.size(outputPath) == 0) {
            Files.deleteIfExists(outputPath);
            throw new IOException("FFmpeg-Clip-Erstellung fehlgeschlagen mit Exit-Code " + rc);
        }

        Path finalPath = storageService.resolveStoragePath(newUuid);
        Files.createDirectories(finalPath.getParent());
        Files.move(outputPath, finalPath, StandardCopyOption.REPLACE_EXISTING);

        String rawTitle = readStringParam(job, "title");
        String trimmedTitle = rawTitle == null ? null : rawTitle.trim();

        String clipName;
        String metadataTitle;
        if (trimmedTitle != null && !trimmedTitle.isEmpty()) {
            clipName = FileNameUtils.sanitize(trimmedTitle) + ".mp4";
            metadataTitle = trimmedTitle;
        } else {
            String baseName = stripExtension(source.getOriginalName());
            clipName = baseName + "_" + formatTimecode(startMs) + "_" + formatTimecode(endMs) + ".mp4";
            metadataTitle = stripExtension(clipName);
        }
        long clipSize = Files.size(finalPath);

        // Resource-Erstellung, Metadaten-Speicherung und Event-Publikation laufen in einer
        // gemeinsamen Transaktion: entweder alles committet, oder alles wird zurückgerollt.
        // Andernfalls könnte z.B. ein fehlschlagender metadataRepository.save() eine verwaiste
        // Resource-Zeile hinterlassen, die bei jedem Retry des Jobs erneut angelegt würde.
        Long newResourceId;
        try {
            newResourceId = new TransactionTemplate(txManager).execute(status -> {
                Resource clip = Resource.builder()
                        .folderId(source.getFolderId())
                        .originalName(clipName)
                        .storedName(newUuid)
                        .mimeType("video/mp4")
                        .size(clipSize)
                        .uploaderId(source.getUploaderId())
                        .storageType(StorageType.LOCAL)
                        .build();
                clip = resourceRepository.save(clip);

                metadataRepository.save(ResourceMetadata.builder()
                        .resourceId(clip.getId())
                        .title(metadataTitle)
                        .build());

                eventPublisher.publishEvent(
                        new ResourceUploadedEvent(clip.getId(), clip.getMimeType(), clip.getFolderId()));

                return clip.getId();
            });
        } catch (RuntimeException e) {
            // DB-Transaktion wurde zurückgerollt, aber die bereits verschobene Clip-Datei
            // liegt noch auf der Platte — best-effort aufräumen, damit kein verwaister
            // File-Rest zurückbleibt.
            try {
                storageService.delete(newUuid);
            } catch (IOException ioe) {
                log.warn("Konnte verwaiste Clip-Datei nach fehlgeschlagenem Commit nicht löschen: {}",
                        newUuid, ioe);
            }
            throw e;
        }

        log.info("Clip erstellt: resource={} -> neue resource={} ({} - {})",
                job.getResourceId(), newResourceId, formatTimecode(startMs), formatTimecode(endMs));

        String resultJson = objectMapper.writeValueAsString(Map.of("newResourceId", newResourceId));
        jobService.markDone(job, resultJson);
    }

    List<String> buildClipCommand(Path input, Path output, long startMs, long endMs, FfmpegTranscodeConfig cfg) {
        double startSec = startMs / 1000.0;
        double durationSec = (endMs - startMs) / 1000.0;
        boolean nvenc = cfg.getVideoCodec().contains("nvenc");
        return new ArrayList<>(List.of(
                storageConfig.getFfmpegPath(),
                "-y",
                "-ss", String.format(Locale.ROOT, "%.3f", startSec),
                "-i", input.toString(),
                "-t", String.format(Locale.ROOT, "%.3f", durationSec),
                "-vcodec", cfg.getVideoCodec(),
                "-acodec", cfg.getAudioCodec(),
                "-profile:a", "aac_low",
                nvenc ? "-cq" : "-crf", String.valueOf(cfg.getCrf()),
                "-preset", cfg.getPreset(),
                "-movflags", "+faststart",
                output.toString()
        ));
    }

    private long readLongParam(ProcessingJob job, String key) {
        try {
            return objectMapper.readTree(job.getResult()).path(key).asLong();
        } catch (Exception e) {
            throw new IllegalStateException("Konnte Job-Parameter '" + key + "' nicht lesen: " + job.getResult(), e);
        }
    }

    // Anders als readLongParam wirft dies bewusst nicht: ein unlesbarer/fehlender Titel ist
    // kein fataler Job-Zustand (im Gegensatz zu fehlenden startMs/endMs) und soll nur zum
    // Fallback auf den auto-generierten Namen führen, nicht den ganzen Clip-Job scheitern lassen.
    private String readStringParam(ProcessingJob job, String key) {
        try {
            if (job.getResult() == null) return null;
            var node = objectMapper.readTree(job.getResult()).path(key);
            return node.isMissingNode() || node.isNull() ? null : node.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(0, dot) : fileName;
    }

    static String formatTimecode(long ms) {
        long totalSeconds = ms / 1000;
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format(Locale.ROOT, "%02d-%02d-%02d", h, m, s);
    }
}
