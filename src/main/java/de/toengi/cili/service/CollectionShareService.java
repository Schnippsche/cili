package de.toengi.cili.service;

import de.toengi.cili.dto.collection.CollectionShareInfoDto;
import de.toengi.cili.dto.collection.CollectionShareTokenDto;
import de.toengi.cili.dto.collection.SharedResourceItemDto;
import de.toengi.cili.dto.media.SubtitleTrackDto;
import de.toengi.cili.dto.testimonial.PublicTestimonialDto;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Collection;
import de.toengi.cili.model.entity.CollectionItem;
import de.toengi.cili.model.entity.CollectionShareToken;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.entity.Thumbnail;
import de.toengi.cili.model.enums.ThumbnailStatus;
import de.toengi.cili.repository.CollectionItemRepository;
import de.toengi.cili.repository.CollectionRepository;
import de.toengi.cili.repository.CollectionShareTokenRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import de.toengi.cili.repository.ThumbnailRepository;
import de.toengi.cili.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollectionShareService {

    private final CollectionShareTokenRepository tokenRepo;
    private final CollectionRepository collectionRepo;
    private final CollectionItemRepository itemRepo;
    private final ResourceRepository resourceRepo;
    private final SubtitleTrackRepository subtitleTrackRepo;
    private final ThumbnailRepository thumbnailRepo;
    private final TestimonialService testimonialService;
    private final StreamService streamService;
    private final ThumbnailService thumbnailService;
    private final StorageService storageService;

    @Value("${cili.share.token-validity-days:90}")
    private int tokenValidityDays;

    @Transactional
    public CollectionShareTokenDto createShare(Long userId, Long collectionId) {
        requireOwned(userId, collectionId);
        tokenRepo.findByCollectionId(collectionId).ifPresent(existing -> {
            tokenRepo.delete(existing);
            tokenRepo.flush();
        });
        CollectionShareToken saved = tokenRepo.save(CollectionShareToken.builder()
                .token(UUID.randomUUID().toString())
                .collectionId(collectionId)
                .createdBy(userId)
                .expiresAt(LocalDateTime.now().plusDays(tokenValidityDays))
                .build());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Optional<CollectionShareTokenDto> getShare(Long userId, Long collectionId) {
        requireOwned(userId, collectionId);
        return tokenRepo.findByCollectionId(collectionId)
                .filter(t -> !isExpired(t))
                .map(this::toDto);
    }

    @Transactional
    public void revokeShare(Long userId, Long collectionId) {
        requireOwned(userId, collectionId);
        tokenRepo.findByCollectionId(collectionId).ifPresent(tokenRepo::delete);
    }

    @Transactional(readOnly = true)
    public CollectionShareInfoDto getInfo(String token) {
        CollectionShareToken shareToken = requireValidToken(token);
        Collection collection = collectionRepo.findById(shareToken.getCollectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Collection", shareToken.getCollectionId()));

        List<CollectionItem> resourceItems = itemRepo
                .findByCollectionIdAndResourceIdIsNotNullOrderByAddedAtDesc(shareToken.getCollectionId());
        List<Long> resourceIds = resourceItems.stream().map(CollectionItem::getResourceId).toList();
        Map<Long, Resource> resourceMap = resourceRepo.findAllById(resourceIds).stream()
                .collect(Collectors.toMap(Resource::getId, r -> r));

        Map<Long, Thumbnail> thumbnailMap = thumbnailRepo.findByResourceIdIn(resourceIds).stream()
                .collect(Collectors.toMap(Thumbnail::getResourceId, t -> t));
        Map<Long, List<SubtitleTrack>> subtitleMap = subtitleTrackRepo.findByResourceIdIn(resourceIds).stream()
                .collect(Collectors.groupingBy(SubtitleTrack::getResourceId));

        List<SharedResourceItemDto> resources = resourceItems.stream()
                .map(item -> resourceMap.get(item.getResourceId()))
                .filter(Objects::nonNull)
                .map(r -> toSharedItem(r, thumbnailMap.get(r.getId()), subtitleMap.getOrDefault(r.getId(), List.of())))
                .toList();

        List<CollectionItem> testimonialItems = itemRepo
                .findByCollectionIdAndTestimonialIdIsNotNullOrderByAddedAtDesc(shareToken.getCollectionId());
        List<Long> testimonialIds = testimonialItems.stream().map(CollectionItem::getTestimonialId).toList();
        List<PublicTestimonialDto> testimonials = testimonialService.getPublicByIds(testimonialIds);

        return new CollectionShareInfoDto(collection.getName(), shareToken.getExpiresAt(), resources, testimonials);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ResourceRegion> streamResource(String token, Long resourceId, HttpHeaders headers)
            throws IOException {
        CollectionShareToken shareToken = requireValidToken(token);
        requireMember(shareToken.getCollectionId(), resourceId);
        return streamService.streamPublic(resourceId, headers);
    }

    @Transactional(readOnly = true)
    public byte[] getThumbnail(String token, Long resourceId, String size) throws IOException {
        CollectionShareToken shareToken = requireValidToken(token);
        requireMember(shareToken.getCollectionId(), resourceId);
        return thumbnailService.getThumbnailBytesNoAcl(resourceId, size);
    }

    @Transactional(readOnly = true)
    public String getSubtitleText(String token, Long resourceId, Long trackId) {
        CollectionShareToken shareToken = requireValidToken(token);
        requireMember(shareToken.getCollectionId(), resourceId);
        SubtitleTrack track = findSubtitleTrack(trackId, resourceId);
        return track.getTextContent() != null ? track.getTextContent() : "";
    }

    @Transactional(readOnly = true)
    public SubtitleService.SubtitleDownload getSubtitle(String token, Long resourceId, Long trackId)
            throws IOException {
        CollectionShareToken shareToken = requireValidToken(token);
        requireMember(shareToken.getCollectionId(), resourceId);
        SubtitleTrack track = findSubtitleTrack(trackId, resourceId);
        if (track.getTextContent() != null) {
            byte[] bytes = track.getTextContent().getBytes(StandardCharsets.UTF_8);
            return new SubtitleService.SubtitleDownload(new ByteArrayInputStream(bytes), track.getFormat());
        }
        return new SubtitleService.SubtitleDownload(
                storageService.retrieve(track.getStoredName()), track.getFormat());
    }

    public int getValidityDays() {
        return tokenValidityDays;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requireOwned(Long userId, Long collectionId) {
        collectionRepo.findByIdAndUserId(collectionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Collection not found or access denied"));
    }

    private CollectionShareToken requireValidToken(String token) {
        CollectionShareToken shareToken = tokenRepo.findByToken(token)
                .orElseThrow(() -> new CiliException("Der Freigabe-Link ist ungültig.", HttpStatus.NOT_FOUND));
        if (isExpired(shareToken)) {
            throw new CiliException("Der Freigabe-Link ist abgelaufen.", HttpStatus.GONE);
        }
        return shareToken;
    }

    private void requireMember(Long collectionId, Long resourceId) {
        if (itemRepo.existsByCollectionIdAndResourceId(collectionId, resourceId)) return;
        Resource r = resourceRepo.findById(resourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        if (r.getTestimonialId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resource not in collection");
        }
        if (itemRepo.existsByCollectionIdAndTestimonialId(collectionId, r.getTestimonialId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resource not in collection");
    }

    private SubtitleTrack findSubtitleTrack(Long trackId, Long resourceId) {
        return subtitleTrackRepo.findByIdAndResourceId(trackId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("SubtitleTrack", trackId));
    }

    private SharedResourceItemDto toSharedItem(Resource r, Thumbnail thumb, List<SubtitleTrack> tracks) {
        boolean hasThumbnail = (thumb != null && thumb.getStatus() == ThumbnailStatus.DONE)
                || r.getMimeType().startsWith("image/");
        List<SubtitleTrackDto> subtitles = tracks.stream().map(this::toSubtitleDto).toList();
        return new SharedResourceItemDto(r.getId(), r.getOriginalName(), r.getMimeType(), hasThumbnail, subtitles);
    }

    private SubtitleTrackDto toSubtitleDto(SubtitleTrack t) {
        return new SubtitleTrackDto(t.getId(), t.getResourceId(),
                t.getLanguageCode(), t.getLabel(), t.getFormat(), t.getCreatedAt(),
                t.getTextContent() != null && !t.getTextContent().isBlank());
    }

    private boolean isExpired(CollectionShareToken token) {
        return LocalDateTime.now().isAfter(token.getExpiresAt());
    }

    private CollectionShareTokenDto toDto(CollectionShareToken t) {
        return new CollectionShareTokenDto(t.getCollectionId(), t.getToken(),
                t.getCreatedAt(), t.getExpiresAt(), tokenValidityDays);
    }
}
