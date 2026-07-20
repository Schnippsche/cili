package de.toengi.cili.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.toengi.cili.dto.search.SearchResponse;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.AclResourceType;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import de.toengi.cili.repository.ThumbnailRepository;
import de.toengi.cili.security.CiliUserDetails;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SearchServicePermissionTest {
  private final Pageable pageable = PageRequest.of(0, 20);
  @Mock
  ResourceRepository resourceRepository;
  @Mock
  ResourceMetadataRepository metadataRepository;
  @Mock
  SubtitleTrackRepository subtitleTrackRepository;
  @Mock
  ThumbnailRepository thumbnailRepository;
  @Mock
  AclService aclService;
  @Mock
  CiliUserDetails userDetails;
  @InjectMocks
  SearchService searchService;

  @BeforeEach
  void setupAuth() {
    lenient().when(userDetails.getUserId()).thenReturn(42L);
    List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
    lenient().doReturn(authorities).when(userDetails).getAuthorities();
    var auth = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void clearAuth() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void folderScopedSearch_noAccess_returnsEmpty() {
    when(aclService.hasPermission(42L, 99L, AclResourceType.FOLDER, AclPermission.READ)).thenReturn(
        false);
    SearchResponse result = searchService.search("test", 99L, null, pageable);
    assertThat(result.totalHits()).isZero();
    assertThat(result.hits()).isEmpty();
    verifyNoInteractions(resourceRepository);
  }

  @Test
  void globalSearch_filtersToAccessibleFolders() {
    when(aclService.getAccessibleFolderIds(42L)).thenReturn(Optional.of(List.of(1L, 2L)));
    when(resourceRepository.findByFolderIdIn(eq(List.of(1L, 2L)),
        eq(pageable))).thenReturn(Page.empty());
    when(metadataRepository.findByResourceIdIn(anyList())).thenReturn(List.of());
    searchService.search("", null, null, pageable);
    verify(resourceRepository).findByFolderIdIn(List.of(1L, 2L), pageable);
    verify(aclService, never()).hasPermission(anyLong(), anyLong(), any(), any());
  }

  @Test
  void globalSearch_noAccessibleFolders_returnsEmpty() {
    when(aclService.getAccessibleFolderIds(42L)).thenReturn(Optional.of(List.of(-1L)));
    when(resourceRepository.findByFolderIdIn(eq(List.of(-1L)),
        eq(pageable))).thenReturn(Page.empty());
    when(metadataRepository.findByResourceIdIn(anyList())).thenReturn(List.of());
    SearchResponse result = searchService.search("", null, null, pageable);
    assertThat(result.totalHits()).isZero();
  }

  @Test
  void folderScopedSearch_withAccess_callsRepository() {
    when(aclService.hasPermission(42L, 5L, AclResourceType.FOLDER, AclPermission.READ)).thenReturn(
        true);
    when(resourceRepository.searchByFolderAndNameOrMetadata(eq(5L),
        eq("+doc*"),
        eq(pageable))).thenReturn(Page.empty());
    when(metadataRepository.findByResourceIdIn(anyList())).thenReturn(List.of());
    searchService.search("doc", 5L, null, pageable);
    verify(resourceRepository).searchByFolderAndNameOrMetadata(5L, "+doc*", pageable);
  }

  @Test
  void adminSearch_bypassesPermissionFilter() {
    doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(userDetails).getAuthorities();
    when(resourceRepository.findAllInFolders((pageable))).thenReturn(Page.empty());
    when(metadataRepository.findByResourceIdIn(anyList())).thenReturn(List.of());
    searchService.search("", null, null, pageable);
    verify(resourceRepository).findAllInFolders(pageable);
    verify(aclService, never()).getAccessibleFolderIds(anyLong());
    verify(aclService, never()).hasPermission(anyLong(), anyLong(), any(), any());
  }
}
