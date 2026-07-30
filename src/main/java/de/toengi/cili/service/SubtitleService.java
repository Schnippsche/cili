package de.toengi.cili.service;

import de.toengi.cili.dto.media.SubtitleTrackDto;
import de.toengi.cili.exception.ConflictException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.FileNameUtils;
import de.toengi.cili.util.SubtitleConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubtitleService {

    private static final int MAX_TEXT_CHARS = 500_000;
    public static final String SUBTITLE_TRACK = "SubtitleTrack";

    private final SubtitleTrackRepository subtitleTrackRepository;
    private final ResourceRepository resourceRepository;
    private final StorageService storageService;
    private final ProcessingJobService jobService;
    private final VideoWorkflowOrchestrator orchestrator;

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public List<SubtitleTrackDto> list(Long resourceId, Long userId) {
        findResourceOrThrow(resourceId);
        return subtitleTrackRepository.findByResourceId(resourceId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'MANAGE_SUBTITLES')")
    public SubtitleTrackDto upload(Long resourceId, String languageCode, String label,
                                   SubtitleFormat format, byte[] fileBytes, Long userId)
            throws IOException {
        findResourceOrThrow(resourceId);
        if (subtitleTrackRepository.existsByResourceIdAndLanguageCode(resourceId, languageCode)) {
            throw new ConflictException("Subtitle track already exists for language: " + languageCode);
        }
        String storedName = storageService.store(
                new ByteArrayInputStream(fileBytes), fileBytes.length);
        try {
            String textContent = extractText(fileBytes, resourceId);
            SubtitleTrack track = subtitleTrackRepository.save(SubtitleTrack.builder()
                    .resourceId(resourceId).languageCode(languageCode)
                    .label(label).storedName(storedName).format(format)
                    .textContent(textContent)
                    .build());
            jobService.cancelWhisperJobIfActive(resourceId);
            log.info("Untertitel hochgeladen: user={} resource={} sprache='{}' format={}", userId, resourceId, languageCode, format);
            return toDto(track);
        } catch (Exception e) {
            storageService.delete(storedName);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public String getText(Long resourceId, Long trackId, Long userId) {
        findResourceOrThrow(resourceId);
        SubtitleTrack track = subtitleTrackRepository.findByIdAndResourceId(trackId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException(SUBTITLE_TRACK, trackId));
        return track.getTextContent() != null ? track.getTextContent() : "";
    }

    public record SubtitleDownload(InputStream stream, SubtitleFormat format) {}

    public record SubtitleExport(byte[] bytes, String filename, String contentType) {}

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public SubtitleExport export(Long resourceId, Long trackId, String targetFormat, Long userId) throws IOException {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", resourceId));
        SubtitleTrack track = subtitleTrackRepository.findByIdAndResourceId(trackId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException(SUBTITLE_TRACK, trackId));

        String content;
        if (track.getTextContent() != null && !track.getTextContent().isBlank()) {
            content = track.getTextContent();
        } else {
            try (InputStream is = storageService.retrieve(track.getStoredName())) {
                content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        String converted;
        String ext;
        String contentType;
        switch (targetFormat.toLowerCase()) {
            case "vtt" -> { converted = SubtitleConverter.toVtt(content, track.getFormat());   ext = "vtt"; contentType = "text/vtt"; }
            case "srt" -> { converted = SubtitleConverter.toSrt(content, track.getFormat());   ext = "srt"; contentType = "text/plain"; }
            case "txt" -> { converted = SubtitleConverter.toPlainText(content);                ext = "txt"; contentType = "text/plain"; }
            default    -> throw new IllegalArgumentException("Unknown subtitle format: " + targetFormat);
        }

        String baseName = FileNameUtils.sanitize(FileNameUtils.getBaseName(resource.getOriginalName()));
        String filename = baseName + "_" + track.getLanguageCode() + "." + ext;
        return new SubtitleExport(converted.getBytes(StandardCharsets.UTF_8), filename, contentType);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public SubtitleDownload download(Long resourceId, Long trackId, Long userId) throws IOException {
        findResourceOrThrow(resourceId);
        SubtitleTrack track = subtitleTrackRepository.findByIdAndResourceId(trackId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException(SUBTITLE_TRACK, trackId));
        if (track.getTextContent() != null) {
            byte[] bytes = track.getTextContent().getBytes(StandardCharsets.UTF_8);
            return new SubtitleDownload(new ByteArrayInputStream(bytes), track.getFormat());
        }
        return new SubtitleDownload(storageService.retrieve(track.getStoredName()), track.getFormat());
    }

    @Transactional
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'MANAGE_SUBTITLES')")
    public void delete(Long resourceId, Long trackId, Long userId) throws IOException {
        findResourceOrThrow(resourceId);
        SubtitleTrack track = subtitleTrackRepository.findByIdAndResourceId(trackId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException(SUBTITLE_TRACK, trackId));
        storageService.delete(track.getStoredName());
        subtitleTrackRepository.delete(track);
        log.info("Untertitel gelöscht: user={} resource={} track={} sprache='{}'", userId, resourceId, trackId, track.getLanguageCode());
    }

    /**
     * ACL-freie Variante von {@link #list(Long, Long)} für den öffentlichen Testimonial-Zugriff —
     * der Aufrufer muss den Zugriff selbst absichern (siehe TestimonialService.assertPublicAttachment()),
     * analog zu ThumbnailService.getThumbnailBytesNoAcl().
     */
    @Transactional(readOnly = true)
    public List<SubtitleTrackDto> listNoAcl(Long resourceId) {
        findResourceOrThrow(resourceId);
        return subtitleTrackRepository.findByResourceId(resourceId)
                .stream().map(this::toDto).toList();
    }

    /**
     * ACL-freie Variante von {@link #download(Long, Long, Long)} für den öffentlichen
     * Testimonial-Zugriff — der Aufrufer muss den Zugriff selbst absichern.
     */
    @Transactional(readOnly = true)
    public SubtitleDownload downloadNoAcl(Long resourceId, Long trackId) throws IOException {
        findResourceOrThrow(resourceId);
        SubtitleTrack track = subtitleTrackRepository.findByIdAndResourceId(trackId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException(SUBTITLE_TRACK, trackId));
        if (track.getTextContent() != null) {
            byte[] bytes = track.getTextContent().getBytes(StandardCharsets.UTF_8);
            return new SubtitleDownload(new ByteArrayInputStream(bytes), track.getFormat());
        }
        return new SubtitleDownload(storageService.retrieve(track.getStoredName()), track.getFormat());
    }

    private void findResourceOrThrow(Long resourceId) {
        resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", resourceId));
    }

    private String extractText(byte[] bytes, Long resourceId) {
        try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            return decoded.length() > MAX_TEXT_CHARS ? decoded.substring(0, MAX_TEXT_CHARS) : decoded;
        } catch (CharacterCodingException e) {
            log.warn("Subtitle bytes for resource {} are not valid UTF-8, storing empty text", resourceId);
            return "";
        }
    }

    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'MANAGE_SUBTITLES')")
    public void retranscribe(Long resourceId, Long userId) {
        findResourceOrThrow(resourceId);
        orchestrator.enqueueRetranscribe(resourceId);
    }

    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public boolean hasActiveTranscriptionJob(Long resourceId, Long userId) {
        return jobService.hasActiveTranscriptionJob(resourceId);
    }

    private SubtitleTrackDto toDto(SubtitleTrack t) {
        return new SubtitleTrackDto(t.getId(), t.getResourceId(),
                t.getLanguageCode(), t.getLabel(), t.getFormat(), t.getCreatedAt(),
                t.getTextContent() != null && !t.getTextContent().isBlank());
    }
}
