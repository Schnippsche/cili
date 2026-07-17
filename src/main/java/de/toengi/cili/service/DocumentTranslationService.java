package de.toengi.cili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.config.NllbConfig;
import de.toengi.cili.util.PythonProcessUtils;
import de.toengi.cili.dto.job.ProcessingJobDto;
import de.toengi.cili.dto.translation.DocumentTranslationRequest;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.ResourceMetadata;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.model.enums.StorageType;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DocumentTranslationService {

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/rtf",
            "text/rtf"
    );
    public static final String TARGET_LANG = "targetLang";

    private final ProcessingJobService jobService;
    private final ProcessingJobRepository processingJobRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceMetadataRepository metadataRepository;
    private final StorageService storageService;
    private final NllbConfig nllbConfig;
    private final CiliGlobalConfig global;
    private final FileStorageConfig storageConfig;
    private final Executor gpuExecutor;
    private final LanguageOptionService languageOptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DocumentTranslationService(
            ProcessingJobService jobService,
            ProcessingJobRepository processingJobRepository,
            ResourceRepository resourceRepository,
            ResourceMetadataRepository metadataRepository,
            StorageService storageService,
            NllbConfig nllbConfig,
            CiliGlobalConfig global,
            FileStorageConfig storageConfig,
            @Qualifier("gpuExecutor") Executor gpuExecutor,
            LanguageOptionService languageOptionService) {
        this.jobService = jobService;
        this.processingJobRepository = processingJobRepository;
        this.resourceRepository = resourceRepository;
        this.metadataRepository = metadataRepository;
        this.storageService = storageService;
        this.nllbConfig = nllbConfig;
        this.global = global;
        this.storageConfig = storageConfig;
        this.gpuExecutor = gpuExecutor;
        this.languageOptionService = languageOptionService;
    }

    private static final List<ProcessingJobStatus> ACTIVE = List.of(
            ProcessingJobStatus.PENDING, ProcessingJobStatus.RUNNING);

    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'TRANSLATE_SUBTITLES')")
    public Map<String, Long> enqueueTranslation(Long resourceId, DocumentTranslationRequest req) {
        // 0. Deduplication: aktiven Job für selbe Zielsprache zurückgeben
        Optional<ProcessingJob> dup = processingJobRepository
                .findByResourceIdAndTypeAndStatusIn(resourceId, ProcessingJobType.DOCUMENT_TRANSLATE, ACTIVE)
                .stream()
                .filter(j -> req.targetLang().equals(parseParam(j, TARGET_LANG)))
                .findFirst();
        if (dup.isPresent()) {
            log.info("DOCUMENT_TRANSLATE für resource {} → {} bereits aktiv — Job {} zurückgegeben",
                    resourceId, req.targetLang(), dup.get().getId());
            return Map.of("jobId", dup.get().getId());
        }

        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", resourceId));

        if (!SUPPORTED_MIME_TYPES.contains(resource.getMimeType())) {
            throw new CiliException(
                    "Unsupported document type: " + resource.getMimeType(), HttpStatus.BAD_REQUEST);
        }
        if (!languageOptionService.getSupportedTranslationCodes().contains(req.sourceLang())) {
            throw new CiliException("Unsupported source language: " + req.sourceLang(), HttpStatus.BAD_REQUEST);
        }
        if (!languageOptionService.getSupportedTranslationCodes().contains(req.targetLang())) {
            throw new CiliException("Unsupported target language: " + req.targetLang(), HttpStatus.BAD_REQUEST);
        }

        String initResult;
        try {
            initResult = objectMapper.writeValueAsString(
                    Map.of("sourceLang", req.sourceLang(), TARGET_LANG, req.targetLang()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize job params", e);
        }
        ProcessingJob job = jobService.createJob(
                resourceId, ProcessingJobType.DOCUMENT_TRANSLATE, null, initResult);

        gpuExecutor.execute(() -> execute(job));

        log.info("Enqueued DOCUMENT_TRANSLATE job {} for resource {} ({} → {})",
                job.getId(), resourceId, req.sourceLang(), req.targetLang());
        return Map.of("jobId", job.getId());
    }

    private String parseParam(ProcessingJob job, String key) {
        try {
            if (job.getResult() == null) return null;
            return objectMapper.readTree(job.getResult()).path(key).asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public List<ProcessingJobDto> getActiveJobs(Long resourceId) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return processingJobRepository
                .findByResourceIdAndTypeAndCreatedAtAfter(
                        resourceId, ProcessingJobType.DOCUMENT_TRANSLATE, since)
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(ProcessingJobDto::from)
                .toList();
    }

    public void execute(ProcessingJob job) {
        if (isCancelled(job)) return;
        jobService.markRunning(job, "doc-translate:" + Thread.currentThread().getName());
        try {
            doTranslate(job);
        } catch (Exception e) {
            log.error("Document translation failed for job {}: {}", job.getId(), e.getMessage(), e);
            jobService.markFailed(job, e.getMessage());
        }
    }

    private void doTranslate(ProcessingJob job) throws Exception {
        var json = objectMapper.readTree(job.getResult());
        String srcLang = json.path("sourceLang").asText();
        String tgtLang = json.path(TARGET_LANG).asText();

        Resource source = resourceRepository.findById(job.getResourceId())
                .orElseThrow(() -> new IllegalStateException("Resource not found: " + job.getResourceId()));

        String origName = source.getOriginalName();
        int dotIdx = origName.lastIndexOf('.');
        String fileExt = dotIdx >= 0 ? "." + origName.substring(dotIdx + 1).toLowerCase().replaceAll("[^a-z0-9]", "") : "";
        Path inputDoc = Files.createTempFile("cili-doctrans-in-", fileExt);
        Path outputTxt = Files.createTempFile("cili-doctrans-out-", ".txt");

        try {
            try (var in = storageService.retrieve(source.getStoredName())) {
                Files.copy(in, inputDoc, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            List<String> cmd = buildCommand(inputDoc, outputTxt, srcLang, tgtLang);
            log.info("Starting document translation for job {} (resource {}): {}",
                    job.getId(), job.getResourceId(), cmd);

            ProcessBuilder pb = PythonProcessUtils.forScript(cmd, global.resolve(nllbConfig.getDocScriptName()));
            pb.redirectErrorStream(false);
            Process proc = pb.start();
            pipeToLog(proc, job.getId());

            long deadline = System.currentTimeMillis()
                    + nllbConfig.getTimeoutMinutes() * 60_000L;
            while (!proc.waitFor(10, TimeUnit.SECONDS)) {
                if (isCancelled(job)) {
                    proc.destroyForcibly();
                    log.info("Job {} cancelled — subprocess killed", job.getId());
                    return;
                }
                if (System.currentTimeMillis() > deadline) {
                    proc.destroyForcibly();
                    throw new IOException("doc_translate_worker.py exceeded timeout (job " + job.getId() + ")");
                }
            }

            int rc = proc.exitValue();
            if (rc != 0) throw new IOException("doc_translate_worker.py exited with code " + rc);
            if (isCancelled(job)) return;

            String translatedText = Files.readString(outputTxt, StandardCharsets.UTF_8);
            Long targetResourceId = storeTranslation(source, translatedText, tgtLang);

            jobService.markDone(job, objectMapper.writeValueAsString(
                    Map.of(TARGET_LANG, tgtLang, "targetResourceId", targetResourceId)));

        } finally {
            Files.deleteIfExists(inputDoc);
            Files.deleteIfExists(outputTxt);
        }
    }

    private Long storeTranslation(Resource source, String translatedText, String targetLang) throws IOException {
        byte[] bytes = translatedText.getBytes(StandardCharsets.UTF_8);
        String storedName = storageService.store(new ByteArrayInputStream(bytes), bytes.length);

        Resource newResource = Resource.builder()
                .folderId(source.getFolderId())
                .originalName(buildOutputName(source.getOriginalName(), targetLang))
                .storedName(storedName)
                .mimeType("text/plain")
                .size((long) bytes.length)
                .uploaderId(source.getUploaderId())
                .storageType(StorageType.LOCAL)
                .build();
        newResource = resourceRepository.save(newResource);

        ResourceMetadata meta = ResourceMetadata.builder()
                .resourceId(newResource.getId())
                .textContent(translatedText)
                .language(targetLang)
                .build();
        metadataRepository.save(meta);

        log.info("Stored translated document as resource {} ({})", newResource.getId(), newResource.getOriginalName());
        return newResource.getId();
    }

    List<String> buildCommand(Path input, Path output, String sourceLang, String targetLang) {
        return new ArrayList<>(List.of(
                global.getPythonPath(), global.resolve(nllbConfig.getDocScriptName()),
                "--input", input.toString(),
                "--output", output.toString(),
                "--source", sourceLang,
                "--target", targetLang,
                "--model", nllbConfig.getModelPath(),
                "--libreoffice", storageConfig.getLibreOfficePath(),
                "--device", nllbConfig.getDevice(),
                "--compute-type", nllbConfig.getComputeType(),
                "--beam-size", String.valueOf(nllbConfig.getBeamSize())
        ));
    }

    String buildOutputName(String originalName, String targetLang) {
        int lastDot = originalName.lastIndexOf('.');
        String stem = lastDot > 0 ? originalName.substring(0, lastDot) : originalName;
        return stem + "." + targetLang + ".txt";
    }

    private boolean isCancelled(ProcessingJob job) {
        ProcessingJob fresh = jobService.reloadJob(job.getId());
        return fresh.getStatus().name().equals("CANCELLED");
    }

    private void pipeToLog(Process proc, long jobId) {
        startPipeThread(proc.getInputStream(), jobId, false);
        startPipeThread(proc.getErrorStream(), jobId, true);
    }

    private void startPipeThread(java.io.InputStream stream, long jobId, boolean isErr) {
        Thread t = new Thread(() -> {
            try (var reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (isErr) log.error("[doc-translate job {}] STDERR: {}", jobId, line);
                    else log.info("[doc-translate job {}] {}", jobId, line);
                }
            } catch (IOException ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
