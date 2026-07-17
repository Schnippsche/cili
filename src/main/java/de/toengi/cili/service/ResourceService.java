package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.dto.resource.*;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.mapper.ResourceMapper;
import de.toengi.cili.model.entity.*;
import de.toengi.cili.repository.*;
import de.toengi.cili.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceMetadataRepository metadataRepository;
    private final ThumbnailRepository thumbnailRepository;
    private final SubtitleTrackRepository subtitleTrackRepository;
    private final ResourceFavoriteRepository favoriteRepository;
    private final StorageService storageService;
    private final FileStorageConfig storageConfig;
    private final ResourceMapper resourceMapper;

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#folderId, 'FOLDER', 'READ')")
    public List<ResourceDto> listByFolder(Long folderId, Long userId) {
        List<Resource> resources = resourceRepository.findByFolderId(folderId);
        return getResourceDtos(resources);
    }

    private List<ResourceDto> getResourceDtos(List<Resource> resources) {
        List<Long> ids = resources.stream().map(Resource::getId).toList();
        Map<Long, ResourceMetadata> metaMap = metadataRepository.findByResourceIdIn(ids)
                .stream().collect(Collectors.toMap(ResourceMetadata::getResourceId, m -> m));
        Map<Long, String> thumbMap = thumbnailRepository.findByResourceIdIn(ids)
                .stream().collect(Collectors.toMap(Thumbnail::getResourceId, t -> t.getStatus().name()));
        java.util.Set<Long> analyzableIds = subtitleTrackRepository.findWithTextContentByResourceIdIn(ids)
                .stream().map(SubtitleTrack::getResourceId).collect(Collectors.toSet());
        return resources.stream()
                .map(r -> resourceMapper.toDto(r, metaMap.get(r.getId()), thumbMap.get(r.getId()), analyzableIds.contains(r.getId())))
                .toList();
    }

    @Transactional
    @PreAuthorize("hasPermission(#folderId, 'FOLDER', 'WRITE')")
    public void reorder(Long folderId, List<Long> resourceIds, Long userId) {
        List<Resource> resources = resourceRepository.findByFolderId(folderId);
        Map<Long, Resource> byId = resources.stream().collect(Collectors.toMap(Resource::getId, r -> r));
        for (int i = 0; i < resourceIds.size(); i++) {
            Resource r = byId.get(resourceIds.get(i));
            if (r != null) {
                r.setSortOrder((long) i);
                resourceRepository.save(r);
            }
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#id, 'RESOURCE', 'READ')")
    public ResourceDto getById(Long id, Long userId) {
        Resource resource = findOrThrow(id);
        return toDto(resource);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#id, 'RESOURCE', 'DOWNLOAD')")
    public Resource download(Long id, Long userId) {
        Resource resource = findOrThrow(id);
        log.info("Download: user={} resource={} file='{}'", userId, id, resource.getOriginalName());
        return resource;
    }

    @Transactional
    @PreAuthorize("hasPermission(#id, 'RESOURCE', 'WRITE')")
    public ResourceDto rename(Long id, String newName, Long userId) {
        Resource resource = findOrThrow(id);
        String oldName = resource.getOriginalName();
        resource.setOriginalName(newName);
        resourceRepository.save(resource);
        log.info("Umbenannt: user={} resource={} '{}' -> '{}'", userId, id, oldName, newName);
        return toDto(resource);
    }

    @Transactional
    @PreAuthorize("hasPermission(#id, 'RESOURCE', 'DELETE')")
    public void delete(Long id, Long userId) {
        Resource resource = findOrThrow(id);

        // Untertitel-Dateien von Platte löschen
        subtitleTrackRepository.findByResourceId(id).forEach(track -> {
            try {
                storageService.delete(track.getStoredName());
            } catch (IOException e) {
                log.warn("Untertitel-Datei konnte nicht gelöscht werden (resource={}, track={}): {}", id, track.getId(), e.getMessage());
            }
        });

        // Thumbnail-Dateien von Platte löschen
        thumbnailRepository.findByResourceId(id).ifPresent(thumb -> {
            var thumbDir = Paths.get(storageConfig.getBasePath(), "thumbnails");
            deleteIfPresent(thumbDir, thumb.getSmallPath());
            deleteIfPresent(thumbDir, thumb.getLargePath());
        });

        // Ressource-Datei und DB-Record löschen (cascaded: subtitle_tracks, thumbnails, metadata, ...)
        try {
            storageService.delete(resource.getStoredName());
        } catch (IOException e) {
            log.warn("Failed to delete stored file for resource {}: {}", id, e.getMessage());
        }
        resourceRepository.delete(resource);
        log.info("Gelöscht: user={} resource={} file='{}'", userId, id, resource.getOriginalName());
    }

    private void deleteIfPresent(java.nio.file.Path dir, String filename) {
        if (filename == null) return;
        try {
            Files.deleteIfExists(dir.resolve(filename));
        } catch (IOException e) {
            log.warn("Thumbnail-Datei konnte nicht gelöscht werden ({}): {}", filename, e.getMessage());
        }
    }

    @Transactional
    @PreAuthorize("hasPermission(#id, 'RESOURCE', 'MANAGE_METADATA')")
    public MetadataDto updateMetadata(Long id, UpdateMetadataRequest req, Long userId) {
        if (!resourceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Resource", id);
        }
        ResourceMetadata meta = metadataRepository.findByResourceId(id)
                .orElse(ResourceMetadata.builder().resourceId(id).build());
        meta.setTitle(req.title());
        meta.setDescription(req.description());
        meta.setTags(req.tags());
        meta.setCategories(req.categories());
        meta.setLanguage(req.language() != null && !req.language().isBlank() ? req.language() : "Deutsch");
        ResourceMetadata saved = metadataRepository.save(meta);
        log.info("Metadaten aktualisiert: user={} resource={} title='{}'", userId, id, req.title());
        return resourceMapper.toMetadataDto(saved);
    }

    @Transactional
    public void addFavorite(Long resourceId, Long userId) {
        if (!resourceRepository.existsById(resourceId)) {
            throw new ResourceNotFoundException("Resource", resourceId);
        }
        favoriteRepository.save(new ResourceFavorite(new ResourceFavoriteId(userId, resourceId), null));
    }

    @Transactional
    public void removeFavorite(Long resourceId, Long userId) {
        favoriteRepository.deleteById(new ResourceFavoriteId(userId, resourceId));
    }

    @Transactional(readOnly = true)
    public List<ResourceDto> getFavorites(Long userId) {
        List<Long> ids = favoriteRepository.findByIdUserId(userId).stream()
                .map(f -> f.getId().getResourceId())
                .toList();
        return resourceRepository.findAllById(ids).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasPermission(#id, 'RESOURCE', 'WRITE') and hasPermission(#newFolderId, 'FOLDER', 'WRITE')")
    public ResourceDto moveResource(Long id, Long newFolderId, Long userId) {
        Resource resource = findOrThrow(id);
        resource.setFolderId(newFolderId);
        resourceRepository.save(resource);
        log.info("Verschoben: user={} resource={} -> folder={}", userId, id, newFolderId);
        return toDto(resource);
    }

    private Resource findOrThrow(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", id));
    }

    private ResourceDto toDto(Resource resource) {
        ResourceMetadata meta = metadataRepository.findByResourceId(resource.getId()).orElse(null);
        String thumbStatus = thumbnailRepository.findByResourceId(resource.getId())
                .map(t -> t.getStatus().name()).orElse(null);
        boolean hasAnalyzableSubtitles = subtitleTrackRepository.existsWithTextContentByResourceId(resource.getId());
        return resourceMapper.toDto(resource, meta, thumbStatus, hasAnalyzableSubtitles);
    }

    @Transactional(readOnly = true)
    public List<ResourceDto> toDtoList(List<Resource> resources) {
        if (resources.isEmpty()) return List.of();
        return getResourceDtos(resources);
    }
}
