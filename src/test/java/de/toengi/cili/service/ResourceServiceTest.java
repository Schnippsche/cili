package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.dto.resource.*;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.mapper.ResourceMapper;
import de.toengi.cili.model.entity.*;
import de.toengi.cili.model.enums.StorageType;
import de.toengi.cili.repository.*;
import de.toengi.cili.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock ResourceRepository resourceRepository;
    @Mock ResourceMetadataRepository metadataRepository;
    @Mock ThumbnailRepository thumbnailRepository;
    @Mock SubtitleTrackRepository subtitleTrackRepository;
    @Mock ResourceFavoriteRepository favoriteRepository;
    @Mock StorageService storageService;
    @Mock FileStorageConfig storageConfig;
    @Mock ResourceMapper resourceMapper;

    @InjectMocks ResourceService resourceService;

    // --- listByFolder ---

    @Test
    void listByFolder_returnsAllResourcesInFolder() {
        Resource r1 = resource(1L, 10L);
        Resource r2 = resource(2L, 10L);
        when(resourceRepository.findByFolderId(10L)).thenReturn(List.of(r1, r2));
        when(metadataRepository.findByResourceIdIn(any())).thenReturn(List.of());
        when(thumbnailRepository.findByResourceIdIn(any())).thenReturn(List.of());
        when(resourceMapper.toDto(any(), any(), any(), anyBoolean())).thenReturn(dto(1L), dto(2L));

        List<ResourceDto> result = resourceService.listByFolder(10L, 1L);

        assertThat(result).hasSize(2);
    }

    // --- getById ---

    @Test
    void getById_existingResource_returnsDto() {
        Resource r = resource(5L, 10L);
        ResourceMetadata meta = metadata(5L);
        when(resourceRepository.findById(5L)).thenReturn(Optional.of(r));
        when(metadataRepository.findByResourceId(5L)).thenReturn(Optional.of(meta));
        when(thumbnailRepository.findByResourceId(5L)).thenReturn(Optional.empty());
        when(resourceMapper.toDto(r, meta, null, false)).thenReturn(dto(5L));

        ResourceDto result = resourceService.getById(5L, 1L);

        assertThat(result.id()).isEqualTo(5L);
    }

    @Test
    void getById_notFound_throws() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.getById(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- download ---

    @Test
    void download_existingResource_returnsEntity() {
        Resource r = resource(3L, 10L);
        when(resourceRepository.findById(3L)).thenReturn(Optional.of(r));

        Resource result = resourceService.download(3L, 1L);

        assertThat(result.getId()).isEqualTo(3L);
    }

    @Test
    void download_notFound_throws() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.download(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- rename ---

    @Test
    void rename_updatesOriginalName() {
        Resource r = resource(4L, 10L);
        when(resourceRepository.findById(4L)).thenReturn(Optional.of(r));
        when(resourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(metadataRepository.findByResourceId(4L)).thenReturn(Optional.empty());
        when(thumbnailRepository.findByResourceId(4L)).thenReturn(Optional.empty());
        when(resourceMapper.toDto(any(), any(), any(), anyBoolean())).thenReturn(dto(4L));

        resourceService.rename(4L, "new-name.mp4", 1L);

        verify(resourceRepository).save(argThat(res -> "new-name.mp4".equals(res.getOriginalName())));
    }

    @Test
    void rename_notFound_throws() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.rename(99L, "x.mp4", 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- delete ---

    @Test
    void delete_deletesFileAndEntity() throws IOException {
        Resource r = resource(6L, 10L);
        when(resourceRepository.findById(6L)).thenReturn(Optional.of(r));
        when(subtitleTrackRepository.findByResourceId(6L)).thenReturn(List.of());
        when(thumbnailRepository.findByResourceId(6L)).thenReturn(Optional.empty());


        resourceService.delete(6L, 1L);

        verify(storageService).delete(r.getStoredName());
        verify(resourceRepository).delete(r);
    }

    @Test
    void delete_storageFailure_stillDeletesEntity() throws IOException {
        Resource r = resource(7L, 10L);
        when(resourceRepository.findById(7L)).thenReturn(Optional.of(r));
        when(subtitleTrackRepository.findByResourceId(7L)).thenReturn(List.of());
        when(thumbnailRepository.findByResourceId(7L)).thenReturn(Optional.empty());

        doThrow(new IOException("disk gone")).when(storageService).delete(any());

        resourceService.delete(7L, 1L);

        verify(resourceRepository).delete(r);
    }

    // --- updateMetadata ---

    @Test
    void updateMetadata_createsIfAbsent() {
        when(resourceRepository.existsById(8L)).thenReturn(true);
        when(metadataRepository.findByResourceId(8L)).thenReturn(Optional.empty());
        when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(resourceMapper.toMetadataDto(any())).thenReturn(new MetadataDto("T", null, null, null, null));

        UpdateMetadataRequest req = new UpdateMetadataRequest("T", null, null, null, null);
        MetadataDto result = resourceService.updateMetadata(8L, req, 1L);

        assertThat(result.title()).isEqualTo("T");
        verify(metadataRepository).save(argThat(m -> "T".equals(m.getTitle())));
    }

    @Test
    void updateMetadata_updatesExisting() {
        when(resourceRepository.existsById(9L)).thenReturn(true);
        ResourceMetadata existing = metadata(9L);
        existing.setTitle("Old");
        when(metadataRepository.findByResourceId(9L)).thenReturn(Optional.of(existing));
        when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(resourceMapper.toMetadataDto(any())).thenReturn(new MetadataDto("New", null, null, null, null));

        UpdateMetadataRequest req = new UpdateMetadataRequest("New", null, null, null, null);
        resourceService.updateMetadata(9L, req, 1L);

        verify(metadataRepository).save(argThat(m -> "New".equals(m.getTitle())));
    }

    // --- favorites ---

    @Test
    void addFavorite_savesFavorite() {
        when(resourceRepository.existsById(10L)).thenReturn(true);

        resourceService.addFavorite(10L, 1L);

        verify(favoriteRepository).save(argThat(f ->
                f.getId().getUserId().equals(1L) && f.getId().getResourceId().equals(10L)));
    }

    @Test
    void addFavorite_resourceNotFound_throws() {
        when(resourceRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> resourceService.addFavorite(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeFavorite_deletesFavorite() {
        resourceService.removeFavorite(10L, 1L);

        verify(favoriteRepository).deleteById(new ResourceFavoriteId(1L, 10L));
    }

    @Test
    void getFavorites_returnsUserFavoriteResources() {
        ResourceFavorite fav = new ResourceFavorite(new ResourceFavoriteId(1L, 5L), LocalDateTime.now());
        Resource r = resource(5L, 10L);
        when(favoriteRepository.findByIdUserId(1L)).thenReturn(List.of(fav));
        when(resourceRepository.findAllById(List.of(5L))).thenReturn(List.of(r));
        when(metadataRepository.findByResourceId(5L)).thenReturn(Optional.empty());
        when(thumbnailRepository.findByResourceId(5L)).thenReturn(Optional.empty());
        when(resourceMapper.toDto(any(), any(), any(), anyBoolean())).thenReturn(dto(5L));

        List<ResourceDto> result = resourceService.getFavorites(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(5L);
    }

    // --- moveResource ---

    @Test
    void moveResource_changesFolder() {
        Resource r = resource(1L, 10L);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(r));
        when(resourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(metadataRepository.findByResourceId(1L)).thenReturn(Optional.empty());
        when(thumbnailRepository.findByResourceId(1L)).thenReturn(Optional.empty());
        ResourceDto movedDto = new ResourceDto(1L, 20L, "file.mp4", "uuid", "video/mp4",
                100L, null, 99L, StorageType.LOCAL,
                null, null, null, null, null, null, false);
        when(resourceMapper.toDto(any(), any(), any(), anyBoolean())).thenReturn(movedDto);

        ResourceDto result = resourceService.moveResource(1L, 20L, 99L);

        assertThat(result.folderId()).isEqualTo(20L);
        verify(resourceRepository).save(argThat(res -> res.getFolderId().equals(20L)));
    }

    @Test
    void moveResource_throwsWhenNotFound() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.moveResource(99L, 5L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- helpers ---

    private Resource resource(Long id, Long folderId) {
        return Resource.builder()
                .id(id).folderId(folderId).originalName("file.mp4")
                .storedName(UUID.randomUUID().toString()).mimeType("video/mp4")
                .size(100L).uploaderId(99L).storageType(StorageType.LOCAL)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private ResourceMetadata metadata(Long resourceId) {
        return ResourceMetadata.builder()
                .id(resourceId).resourceId(resourceId)
                .title("Title").updatedAt(LocalDateTime.now())
                .build();
    }

    private ResourceDto dto(Long id) {
        return new ResourceDto(id, 10L, "file.mp4", "uuid", "video/mp4",
                100L, null, 99L, StorageType.LOCAL,
                null, null, LocalDateTime.now(), LocalDateTime.now(), null, null, false);
    }
}
