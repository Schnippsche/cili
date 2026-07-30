package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.dto.upload.CompleteUploadResponse;
import de.toengi.cili.dto.upload.InitUploadRequest;
import de.toengi.cili.dto.upload.UploadJobDto;
import de.toengi.cili.event.ResourceUploadedEvent;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.BulkImportItem;
import de.toengi.cili.model.entity.BulkImportJob;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.ResourceMetadata;
import de.toengi.cili.model.entity.UploadJob;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.BulkImportItemStatus;
import de.toengi.cili.model.enums.StorageType;
import de.toengi.cili.model.enums.UploadJobStatus;
import de.toengi.cili.repository.BulkImportItemRepository;
import de.toengi.cili.repository.BulkImportJobRepository;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.UploadJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    private final UploadJobRepository uploadJobRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceMetadataRepository metadataRepository;
    private final FileStorageConfig storageConfig;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager txManager;
    private final BulkImportItemRepository bulkImportItemRepository;
    private final BulkImportJobRepository bulkImportJobRepository;
    private final AclService aclService;

    private static final java.util.Set<String> BLOCKED_MIME_TYPES = java.util.Set.of(
            "text/html", "application/xhtml+xml",
            "text/javascript", "application/javascript",
            "image/svg+xml"
    );

    @Transactional
    public UploadJobDto initUpload(InitUploadRequest req, Long userId) {
        if ((req.folderId() == null) == (req.testimonialId() == null)) {
            throw new CiliException("Entweder folderId oder testimonialId muss gesetzt sein", HttpStatus.BAD_REQUEST);
        }
        if (req.testimonialId() != null && !aclService.hasTestimonialsPermission(userId, AclPermission.WRITE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Anhängen von Medien");
        }

        String mime = normalizeMimeType(req.fileName(), req.mimeType());

        BulkImportItem bulkItem = req.bulkImportItemId() != null
                ? loadAndValidateBulkItem(req.bulkImportItemId(), userId)
                : null;

        if (BLOCKED_MIME_TYPES.contains(mime.toLowerCase())) {
            if (bulkItem != null) {
                // Must commit independently — see markBulkItemFailed() javadoc: this method is
                // itself @Transactional and about to throw, which would otherwise roll back the
                // FAILED-transition along with everything else in this transaction.
                markBulkItemFailed(bulkItem, "Dateityp nicht erlaubt: " + mime);
            }
            throw new CiliException("Dateityp nicht erlaubt: " + mime, HttpStatus.BAD_REQUEST);
        }

        if (bulkItem != null) {
            bulkItem.setStatus(BulkImportItemStatus.UPLOADING);
            bulkImportItemRepository.save(bulkItem);
        }

        int chunksTotal = (int) Math.ceil((double) req.totalSize() / req.chunkSize());
        UploadJob job = UploadJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .folderId(req.folderId())
                .testimonialId(req.testimonialId())
                .fileName(req.fileName())
                .mimeType(mime)
                .totalSize(req.totalSize())
                .chunkSize(req.chunkSize())
                .chunksTotal(chunksTotal)
                .fileLastModified(req.fileLastModified())
                .bulkImportItemId(req.bulkImportItemId())
                .status(UploadJobStatus.INITIATED)
                .expiresAt(LocalDateTime.now().plusHours(2))
                .build();
        return toDto(uploadJobRepository.save(job));
    }

    /**
     * Loads the BulkImportItem + its parent job and validates ownership and terminal-state
     * before initUpload proceeds. Throws ResourceNotFoundException / AccessDeniedException /
     * CiliException on any validation failure.
     */
    private BulkImportItem loadAndValidateBulkItem(Long bulkImportItemId, Long userId) {
        BulkImportItem item = bulkImportItemRepository.findById(bulkImportItemId)
                .orElseThrow(() -> new ResourceNotFoundException("BulkImportItem", bulkImportItemId));
        BulkImportJob bulkJob = bulkImportJobRepository.findById(item.getBulkImportJobId())
                .orElseThrow(() -> new ResourceNotFoundException("BulkImportJob", item.getBulkImportJobId()));
        if (!bulkJob.getAdminUserId().equals(userId)) {
            throw new AccessDeniedException("Not your bulk import item");
        }
        if (item.getStatus() == BulkImportItemStatus.DONE) {
            throw new CiliException("BulkImportItem bereits abgeschlossen", HttpStatus.BAD_REQUEST);
        }
        return item;
    }

    /**
     * Marks the BulkImportItem FAILED and increments the parent job's failure counter in a
     * brand-new, independently committed transaction (PROPAGATION_REQUIRES_NEW), so the change
     * survives even though the caller (initUpload) is about to throw and roll back its own
     * enclosing @Transactional.
     *
     * Why this is needed: initUpload runs inside a Spring-managed transaction. Under Spring's
     * default rollback rules, throwing any unchecked exception (as initUpload does right after
     * calling this method) rolls back everything done in that transaction — including a plain
     * inline `bulkItemRepository.save(...)` / `incrementFilesFailed(...)` call. Without this
     * REQUIRES_NEW isolation, the BulkImportItem would never actually reach FAILED in the
     * database and would stay stuck at PENDING/UPLOADING forever, and the job's filesFailed
     * counter would never increment — exactly the stuck-forever scenario this logic exists to
     * prevent.
     *
     * IMPORTANT: this is deliberately implemented with an explicit TransactionTemplate rather
     * than `@Transactional(propagation = Propagation.REQUIRES_NEW)` on a private method. This
     * project uses proxy-based Spring AOP (no AspectJ compile-/load-time weaving), so a
     * same-class self-invocation (`this.markBulkItemFailed(...)`) of an @Transactional method
     * would bypass the proxy entirely and silently join the caller's existing transaction
     * instead of starting a new one — reproducing the exact bug this method fixes.
     *
     * Terminal-state guard: the frontend's upload hook retries initUpload on any thrown error,
     * including this deterministic blocked-MIME rejection — so this method can be invoked
     * several times for the same BulkImportItem. Mirrors the no-op pattern already used in
     * BulkImportService.failItem: if the item is already FAILED, skip the mutation entirely so
     * repeated retries don't increment filesFailed more than once for a single failure.
     */
    private void markBulkItemFailed(BulkImportItem bulkItem, String errorMessage) {
        if (bulkItem.getStatus() == BulkImportItemStatus.FAILED) {
            return; // bereits terminal – No-Op (Schutz gegen mehrfachen Fail-Call bei Frontend-Retry)
        }
        TransactionTemplate requiresNew = new TransactionTemplate(txManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(status -> {
            bulkItem.setStatus(BulkImportItemStatus.FAILED);
            bulkItem.setErrorMessage(errorMessage);
            bulkImportItemRepository.save(bulkItem);
            bulkImportJobRepository.incrementFilesFailed(bulkItem.getBulkImportJobId());
        });
    }

    @Transactional
    public UploadJobDto uploadChunk(String jobId, int chunkIndex, InputStream data, Long actorUserId) {
        UploadJob job = getJobForUser(jobId, actorUserId);
        if (job.getStatus() == UploadJobStatus.COMPLETE || job.getStatus() == UploadJobStatus.FAILED) {
            throw new IllegalStateException("Upload job is already " + job.getStatus());
        }
        if (chunkIndex < 0 || chunkIndex >= job.getChunksTotal()) {
            throw new IllegalArgumentException(
                    "Invalid chunk index: " + chunkIndex + " (total: " + job.getChunksTotal() + ")");
        }
        try {
            Path chunkDir = chunkDir(jobId);
            Files.createDirectories(chunkDir);

            // Für große Dateien: Streaming mit großem Buffer (64KB)
            Path chunkPath = chunkDir.resolve(String.valueOf(chunkIndex));
            try (OutputStream out = Files.newOutputStream(chunkPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[65536]; // 64KB buffer
                int bytesRead;
                while ((bytesRead = data.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            try (Stream<Path> files = Files.list(chunkDir)) {
                job.setChunksReceived((int) files.count());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        job.setStatus(UploadJobStatus.IN_PROGRESS);
        return toDto(uploadJobRepository.save(job));
    }

    public CompleteUploadResponse completeUpload(String jobId, Long actorUserId, boolean pinToTop) {
        // 1. Load job (Spring Data opens its own short transaction)
        UploadJob job = getJobForUser(jobId, actorUserId);

        // 2. Guard against duplicate completion
        if (job.getStatus() == UploadJobStatus.COMPLETE || job.getStatus() == UploadJobStatus.FAILED) {
            throw new IllegalStateException("Upload job is already " + job.getStatus());
        }

        // 3. Validate all chunks exist on disk
        Path chunkDir = chunkDir(jobId);
        for (int i = 0; i < job.getChunksTotal(); i++) {
            if (!Files.exists(chunkDir.resolve(String.valueOf(i)))) {
                throw new IllegalStateException(
                        "Missing chunk " + i + " of " + job.getChunksTotal());
            }
        }

        // 4. Assemble chunks to final file (pure I/O — no DB connection held, streaming)
        String storedName = UUID.randomUUID().toString();
        try {
            Path destDir = Paths.get(storageConfig.getBasePath(), "resources", storedName.substring(0, 2));
            Files.createDirectories(destDir);
            Path finalPath = destDir.resolve(storedName);

            // Streaming mit großem Buffer für 2GB+ Dateien
            try (OutputStream out = Files.newOutputStream(finalPath)) {
                byte[] buffer = new byte[1048576]; // 1MB buffer
                for (int i = 0; i < job.getChunksTotal(); i++) {
                    try (InputStream in = Files.newInputStream(chunkDir.resolve(String.valueOf(i)))) {
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // 4.5 Extract text content for search indexing (before transaction)
        Path assembledPath = Paths.get(storageConfig.getBasePath(), "resources", storedName.substring(0, 2), storedName);
        final String textContent = extractTextContent(job.getMimeType(), assembledPath);

        // 5. Short transaction: save Resource, update job status, publish event
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Long resourceId = tx.execute(status -> {
            LocalDateTime fileDate = job.getFileLastModified() != null
                    ? LocalDateTime.ofInstant(Instant.ofEpochMilli(job.getFileLastModified()), ZoneId.systemDefault())
                    : null;
            if (pinToTop) {
                resourceRepository.incrementSortOrderByFolderId(job.getFolderId());
            }
            Resource resource = Resource.builder()
                    .folderId(job.getFolderId())
                    .testimonialId(job.getTestimonialId())
                    .originalName(job.getFileName())
                    .storedName(storedName)
                    .mimeType(job.getMimeType())
                    .size(job.getTotalSize())
                    .uploaderId(job.getUserId())
                    .storageType(StorageType.LOCAL)
                    .fileDate(fileDate)
                    .sortOrder(pinToTop ? 0L : null)
                    .build();
            Resource savedResource = resourceRepository.save(resource);
            String title = stripExtension(job.getFileName());
            metadataRepository.save(ResourceMetadata.builder()
                    .resourceId(savedResource.getId()).title(title).textContent(textContent).build());
            job.setStatus(UploadJobStatus.COMPLETE);
            job.setStoredName(storedName);
            uploadJobRepository.save(job);
            if (job.getBulkImportItemId() != null) {
                // Atomic conditional UPDATE (not read-then-write) — closes the race where two
                // near-simultaneous completions of the same BulkImportItem (e.g. a duplicate or
                // resumed UploadJob) could otherwise both observe "not yet DONE" before either
                // commits, and both increment filesDone.
                bulkImportItemRepository.findById(job.getBulkImportItemId()).ifPresent(item -> {
                    int updated = bulkImportItemRepository.markDoneIfNotAlreadyDone(item.getId(), savedResource.getId());
                    if (updated == 1) {
                        item.setStatus(BulkImportItemStatus.DONE);
                        item.setResourceId(savedResource.getId());
                        bulkImportJobRepository.incrementFilesDone(item.getBulkImportJobId());
                    }
                });
            }
            eventPublisher.publishEvent(
                    new ResourceUploadedEvent(savedResource.getId(), savedResource.getMimeType(), savedResource.getFolderId()));
            return savedResource.getId();
        });

        // 6. Best-effort chunk cleanup — runs after transaction has committed
        deleteDirectory(chunkDir);

        log.info("Upload abgeschlossen: user={} file='{}' size={} resourceId={}",
                actorUserId, job.getFileName(), job.getTotalSize(), resourceId);

        String codecWarning = detectVideoCodecWarning(job.getMimeType(), assembledPath);
        return new CompleteUploadResponse(resourceId, codecWarning);
    }

    @Transactional(readOnly = true)
    public UploadJobDto getStatus(String jobId, Long actorUserId) {
        return toDto(getJobForUser(jobId, actorUserId));
    }

    @Transactional
    public void cancelUpload(String jobId, Long actorUserId) {
        UploadJob job = getJobForUser(jobId, actorUserId);
        deleteDirectory(chunkDir(jobId));
        uploadJobRepository.delete(job);
    }

    private UploadJob getJobForUser(String jobId, Long actorUserId) {
        UploadJob job = uploadJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("UploadJob", jobId));
        if (!job.getUserId().equals(actorUserId)) {
            throw new AccessDeniedException("Not your upload job");
        }
        return job;
    }

    private Path chunkDir(String jobId) {
        return Paths.get(storageConfig.getBasePath(), "uploads", jobId);
    }

    private void deleteDirectory(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (Stream<Path> files = Files.walk(dir)) {
                    files.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException e) {
                                    log.debug("Failed to delete {}", p, e);
                                }
                            });
                }
            }
        } catch (IOException e) {
            log.debug("Failed to walk chunk directory {}", dir, e);
        }
    }

    private UploadJobDto toDto(UploadJob job) {
        return new UploadJobDto(job.getId(), job.getChunksTotal(),
                job.getChunksReceived(), job.getStatus());
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(0, dot) : fileName;
    }

    private String extractTextContent(String mimeType, Path filePath) {
        if (!isIndexableText(mimeType)) return null;
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return content.length() > 100_000 ? content.substring(0, 100_000) : content;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isIndexableText(String mimeType) {
        if (mimeType == null) return false;
        return mimeType.startsWith("text/")
                || mimeType.equals("application/json")
                || mimeType.equals("application/x-subrip");
    }

    private static final java.util.Set<String> BROWSER_COMPATIBLE_VIDEO_CODECS =
            java.util.Set.of("h264", "vp8", "vp9", "av1");
    private static final java.util.Set<String> BROWSER_COMPATIBLE_AUDIO_CODECS =
            java.util.Set.of("aac", "mp3", "opus", "vorbis", "flac", "pcm_s16le", "pcm_s24le", "pcm_f32le");
    // AAC sub-profiles that browsers do NOT support
    private static final java.util.Set<String> UNSUPPORTED_AAC_PROFILES =
            java.util.Set.of("eld", "he-aac v2", "he-aac");

    private String detectVideoCodecWarning(String mimeType, Path filePath) {
        if (mimeType == null) return null;
        boolean isVideo = mimeType.startsWith("video/");
        boolean isAudio = mimeType.startsWith("audio/");
        if (!isVideo && !isAudio) return null;

        if (isVideo) {
            String videoCodec = ffprobeCodec(filePath, "v:0", false);
            if (videoCodec == null) return null;
            if (videoCodec.isEmpty()) {
                return "Die Datei enthält keinen Video-Stream und kann im Browser-Videoplayer nicht abgespielt werden.";
            }
            if (!BROWSER_COMPATIBLE_VIDEO_CODECS.contains(videoCodec)) {
                return "Der Video-Codec \"" + videoCodec + "\" wird von Browsern nicht unterstützt. " +
                        "Das Video kann im Browser möglicherweise nicht abgespielt werden.";
            }
        }

        // Check audio stream for both audio and video files
        String audioCodec = ffprobeCodec(filePath, "a:0", false);
        if (audioCodec == null || audioCodec.isEmpty()) return null;
        if (!BROWSER_COMPATIBLE_AUDIO_CODECS.contains(audioCodec)) {
            return "Der Audio-Codec \"" + audioCodec + "\" wird von Browsern nicht unterstützt. " +
                    "Die Datei kann im Browser möglicherweise nicht abgespielt werden.";
        }
        if (audioCodec.equals("aac")) {
            String profile = ffprobeCodec(filePath, "a:0", true);
            if (profile != null && UNSUPPORTED_AAC_PROFILES.contains(profile.toLowerCase())) {
                return "Das AAC-Profil \"" + profile + "\" wird von Browsern nicht unterstützt (nur AAC-LC ist kompatibel). " +
                        "Die Datei kann im Browser möglicherweise nicht abgespielt werden.";
            }
        }
        return null;
    }

    private String ffprobeCodec(Path filePath, String stream, boolean queryProfile) {
        Process p = null;
        try {
            String entry = queryProfile ? "stream=profile" : "stream=codec_name";
            p = new ProcessBuilder(
                    storageConfig.getFfprobePath(),
                    "-v", "error",
                    "-select_streams", stream,
                    "-show_entries", entry,
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    filePath.toString())
                    .redirectErrorStream(true)
                    .start();
            String result;
            try (InputStream is = p.getInputStream()) {
                result = new String(is.readAllBytes()).strip().toLowerCase();
            }
            p.waitFor();
            return result;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException e) {
            log.debug("ffprobe check failed for {}: {}", filePath.getFileName(), e.getMessage());
            return null;
        } finally {
            if (p != null) p.destroyForcibly();
        }
    }

    /**
     * Browsers often send generic MIME types for text-based formats — fix by extension.
     */
    static String normalizeMimeType(String fileName, String mimeType) {
        if (fileName == null) return mimeType;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".srt")) return "text/plain";
        if (lower.endsWith(".vtt")) return "text/vtt";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".xml")) return "text/xml";
        if (lower.endsWith(".ogg") || lower.endsWith(".oga")) return "audio/ogg";
        if (lower.endsWith(".ogv")) return "video/ogg";
        return mimeType;
    }
}
