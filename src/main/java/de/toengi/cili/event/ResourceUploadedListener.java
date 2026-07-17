package de.toengi.cili.event;

import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.EmbeddedSubtitleExtractService;
import de.toengi.cili.service.TextExtractionService;
import de.toengi.cili.service.ThumbnailService;
import de.toengi.cili.service.VideoWorkflowOrchestrator;
import de.toengi.cili.service.VttAutoAssociationService;
import de.toengi.cili.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.nio.file.Path;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceUploadedListener {

    private final ThumbnailService thumbnailService;
    private final VideoWorkflowOrchestrator videoWorkflowOrchestrator;
    private final VttAutoAssociationService vttAutoAssociationService;
    private final ResourceRepository resourceRepository;
    private final TextExtractionService textExtractionService;
    private final EmbeddedSubtitleExtractService embeddedSubtitleExtractService;
    private final StorageService storageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("thumbnailExecutor")
    public void onResourceUploaded(ResourceUploadedEvent event) {
        log.info("Resource uploaded: id={}, mimeType={}", event.resourceId(), event.mimeType());

        try {
            thumbnailService.processUpload(event.resourceId(), event.mimeType());
        } catch (Exception e) {
            log.warn("Thumbnail processing failed for resource {}", event.resourceId(), e);
        }

        if (event.mimeType().startsWith("video/")) {
            handleVideoUpload(event);
        } else if (event.mimeType().startsWith("audio/")) {
            handleAudioUpload(event);
        } else if ("text/vtt".equals(event.mimeType())) {
            handleVttUpload(event);
        } else if (textExtractionService.supports(event.mimeType())) {
            try {
                textExtractionService.extractAndIndex(event.resourceId());
            } catch (Exception e) {
                log.warn("Text extraction failed for resource {}", event.resourceId(), e);
            }
        }
    }

    private void handleVideoUpload(ResourceUploadedEvent event) {
        Resource resource = resourceRepository.findById(event.resourceId()).orElse(null);
        if (resource == null) return;

        boolean vttFound = vttAutoAssociationService.onVideoUploaded(
            event.resourceId(), resource.getOriginalName(), event.folderId());

        if (vttFound) {
            log.info("VTT found for video {} — skipping Whisper transcription", event.resourceId());
            videoWorkflowOrchestrator.enqueueVideoWorkflowWithoutWhisper(event.resourceId());
            return;
        }

        int embeddedCount = 0;
        try {
            Optional<Path> pathOpt =
                storageService.resolveLocalPath(resource.getStoredName());
            if (pathOpt.isPresent()) {
                embeddedCount = embeddedSubtitleExtractService.extractAndStore(
                    event.resourceId(), pathOpt.get());
            } else {
                log.debug("[EMBEDDED-SUB] Storage path unavailable for resource {} — skipping embedded extraction",
                    event.resourceId());
            }
        } catch (Exception e) {
            log.warn("[EMBEDDED-SUB] Extraction failed for resource {}", event.resourceId(), e);
        }

        if (embeddedCount > 0) {
            log.info("[EMBEDDED-SUB] Found {} embedded track(s) for video {} — skipping Whisper",
                embeddedCount, event.resourceId());
            videoWorkflowOrchestrator.enqueueVideoWorkflowWithoutWhisper(event.resourceId());
        } else {
            videoWorkflowOrchestrator.enqueueVideoWorkflow(event.resourceId());
        }
    }

    private void handleAudioUpload(ResourceUploadedEvent event) {
        Resource resource = resourceRepository.findById(event.resourceId()).orElse(null);
        if (resource == null) return;

        boolean vttFound = vttAutoAssociationService.onVideoUploaded(
            event.resourceId(), resource.getOriginalName(), event.folderId());

        if (vttFound) {
            log.info("VTT found for audio {} — skipping Whisper transcription", event.resourceId());
        } else {
            videoWorkflowOrchestrator.startAudioWorkflow(event.resourceId());
        }
    }

    private void handleVttUpload(ResourceUploadedEvent event) {
        Resource resource = resourceRepository.findById(event.resourceId()).orElse(null);
        if (resource == null) return;

        vttAutoAssociationService.onVttUploaded(
            event.resourceId(), resource.getOriginalName(), event.folderId());
    }
}
