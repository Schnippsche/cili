package de.toengi.cili.service;

import de.toengi.cili.dto.search.FacetsResponse;
import de.toengi.cili.dto.search.SearchResponse;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.enums.StorageType;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import de.toengi.cili.repository.TestimonialRepository;
import de.toengi.cili.repository.ThumbnailRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock ResourceRepository resourceRepository;
    @Mock ResourceMetadataRepository metadataRepository;
    @Mock SubtitleTrackRepository subtitleTrackRepository;
    @Mock ThumbnailRepository thumbnailRepository;
    @Mock TestimonialRepository testimonialRepository;
    @Mock AclService aclService;
    @Mock EntityManager entityManager;

    @InjectMocks
    SearchService searchService;

    @Test
    void search_singleTerm_callsNameOrMetadata() {
        Resource resource = resource(1L);
        when(resourceRepository.searchByNameOrMetadata(eq("+video*"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resource)));
        when(metadataRepository.findByResourceIdIn(any())).thenReturn(List.of());
        when(subtitleTrackRepository.findWithTextContentByResourceIdIn(any())).thenReturn(List.of());

        SearchResponse response = searchService.search("video", null, null, PageRequest.of(0, 20));

        assertThat(response.totalHits()).isEqualTo(1L);
        assertThat(response.hits()).hasSize(1);
        assertThat(response.hits().get(0).resourceId()).isEqualTo(1L);
    }

    @Test
    void search_emptyQuery_returnsAll() {
        when(resourceRepository.findAllInFolders(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        SearchResponse response = searchService.search("", null, null, PageRequest.of(0, 20));

        assertThat(response.hits()).isEmpty();
        verify(resourceRepository).findAllInFolders(any(Pageable.class));
    }

    @Test
    void search_withFolderFilter_usesFolder() {
        when(resourceRepository.searchByFolderAndNameOrMetadata(eq(5L), eq("+doc*"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(metadataRepository.findByResourceIdIn(any())).thenReturn(List.of());

        searchService.search("doc", 5L, null, PageRequest.of(0, 20));

        verify(resourceRepository).searchByFolderAndNameOrMetadata(eq(5L), eq("+doc*"), any(Pageable.class));
    }

    @Test
    void getFacets_returnsMimeTypeAndLanguageCounts() {
        when(resourceRepository.countByMimeType(anyString()))
                .thenReturn(List.of(new Object[]{"video/mp4", 3L}, new Object[]{"application/pdf", 1L}));
        when(resourceRepository.countByLanguage(anyString())).thenReturn(List.of());

        FacetsResponse facets = searchService.getFacets("video");

        assertThat(facets.mimeTypes()).hasSize(2);
        assertThat(facets.mimeTypes().get(0).value()).isEqualTo("video/mp4");
        assertThat(facets.mimeTypes().get(0).count()).isEqualTo(3L);
        assertThat(facets.languages()).isEmpty();
    }

    private Resource resource(Long id) {
        return Resource.builder()
                .id(id).folderId(1L).originalName("video.mp4")
                .storedName("uuid").mimeType("video/mp4")
                .size(1024L).uploaderId(1L).storageType(StorageType.LOCAL)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
