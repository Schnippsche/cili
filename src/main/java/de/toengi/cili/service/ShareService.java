package de.toengi.cili.service;

import de.toengi.cili.dto.media.SubtitleTrackDto;
import de.toengi.cili.dto.share.ShareInfoDto;
import de.toengi.cili.dto.share.ShareTokenDto;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.ShareToken;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.ShareTokenRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import de.toengi.cili.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareTokenRepository shareTokenRepository;
    private final ResourceRepository resourceRepository;
    private final SubtitleTrackRepository subtitleTrackRepository;
    private final StreamService streamService;
    private final StorageService storageService;

    @Value("${cili.share.token-validity-days:90}")
    private int tokenValidityDays;

    @Value("${cili.share.base-url:}")
    private String shareBaseUrl;

    @Transactional
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'SHARE')")
    public ShareTokenDto createShare(Long resourceId, Long userId) {
        Optional<ShareToken> existing = shareTokenRepository.findByResourceId(resourceId);
        if (existing.isPresent()) {
            ShareToken t = existing.get();
            if (!isExpired(t)) return toDto(t);
            shareTokenRepository.delete(t);
        }
        ShareToken token = ShareToken.builder()
                .token(UUID.randomUUID().toString())
                .resourceId(resourceId)
                .createdBy(userId)
                .expiresAt(LocalDateTime.now().plusDays(tokenValidityDays))
                .build();
        return toDto(shareTokenRepository.save(token));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'SHARE')")
    public Optional<ShareTokenDto> getShare(Long resourceId, Long userId) {
        return shareTokenRepository.findByResourceId(resourceId)
                .filter(t -> !isExpired(t))
                .map(this::toDto);
    }

    @Transactional
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'SHARE')")
    public void revokeShare(Long resourceId, Long userId) {
        shareTokenRepository.findByResourceId(resourceId)
                .ifPresent(shareTokenRepository::delete);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ResourceRegion> streamByToken(String token, HttpHeaders headers)
            throws IOException {
        ShareToken shareToken = requireValidToken(token);
        return streamService.streamPublic(shareToken.getResourceId(), headers);
    }

    @Transactional(readOnly = true)
    public ShareInfoDto getShareInfo(String token) {
        ShareToken shareToken = requireValidToken(token);
        Resource resource = resourceRepository.findById(shareToken.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Resource", shareToken.getResourceId()));
        List<SubtitleTrackDto> subtitles = subtitleTrackRepository.findByResourceId(shareToken.getResourceId())
                .stream().map(this::toSubtitleDto).toList();
        return new ShareInfoDto(resource.getOriginalName(), resource.getMimeType(), subtitles);
    }

    @Transactional(readOnly = true)
    public String getSubtitleText(String token, Long trackId) {
        SubtitleTrack track = findSubtitleByToken(token, trackId);
        return track.getTextContent() != null ? track.getTextContent() : "";
    }

    @Transactional(readOnly = true)
    public SubtitleService.SubtitleDownload getSubtitleDownload(String token, Long trackId) throws IOException {
        SubtitleTrack track = findSubtitleByToken(token, trackId);
        if (track.getTextContent() != null) {
            byte[] bytes = track.getTextContent().getBytes(StandardCharsets.UTF_8);
            return new SubtitleService.SubtitleDownload(new ByteArrayInputStream(bytes), track.getFormat());
        }
        return new SubtitleService.SubtitleDownload(storageService.retrieve(track.getStoredName()), track.getFormat());
    }

    private ShareToken requireValidToken(String token) {
        ShareToken shareToken = shareTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("ShareToken", 0L));
        if (isExpired(shareToken)) {
            throw new CiliException("Der Freigabe-Link ist abgelaufen.", HttpStatus.GONE);
        }
        return shareToken;
    }

    private SubtitleTrack findSubtitleByToken(String token, Long trackId) {
        ShareToken shareToken = requireValidToken(token);
        return subtitleTrackRepository.findByIdAndResourceId(trackId, shareToken.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException("SubtitleTrack", trackId));
    }

    private boolean isExpired(ShareToken token) {
        return LocalDateTime.now().isAfter(token.getExpiresAt());
    }

    private SubtitleTrackDto toSubtitleDto(SubtitleTrack t) {
        return new SubtitleTrackDto(t.getId(), t.getResourceId(),
                t.getLanguageCode(), t.getLabel(), t.getFormat(), t.getCreatedAt(),
                t.getTextContent() != null && !t.getTextContent().isBlank());
    }

    public int getValidityDays() {
        return tokenValidityDays;
    }

    public String getShareBaseUrl() {
        return shareBaseUrl;
    }

    private ShareTokenDto toDto(ShareToken token) {
        return new ShareTokenDto(token.getResourceId(), token.getToken(),
                token.getCreatedAt(), token.getExpiresAt(), tokenValidityDays);
    }
}
