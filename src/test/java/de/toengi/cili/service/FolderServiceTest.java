package de.toengi.cili.service;

import de.toengi.cili.dto.folder.*;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.mapper.FolderMapper;
import de.toengi.cili.model.entity.Folder;
import de.toengi.cili.model.entity.FolderFavorite;
import de.toengi.cili.model.entity.FolderFavoriteId;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.AclResourceType;
import de.toengi.cili.repository.AclEntryRepository;
import de.toengi.cili.repository.FolderFavoriteRepository;
import de.toengi.cili.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @Mock private FolderRepository folderRepository;
    @Mock private FolderFavoriteRepository favoriteRepository;
    @Mock private AclEntryRepository aclEntryRepository;
    @Mock private AclService aclService;
    @Mock private FolderMapper folderMapper;

    @InjectMocks private FolderService folderService;

    private Folder rootFolder;
    private Folder childFolder;
    private Folder grandchildFolder;

    @BeforeEach
    void setUp() {
        rootFolder = Folder.builder()
                .id(1L).name("Root").path("/1/").createdBy(99L).build();
        childFolder = Folder.builder()
                .id(2L).name("Child").parentId(1L).path("/1/2/").createdBy(99L).build();
        grandchildFolder = Folder.builder()
                .id(3L).name("Grandchild").parentId(2L).path("/1/2/3/").createdBy(99L).build();
    }

    // --- createFolder ---

    @Test
    void createRootFolder_setsCorrectPath() {
        CreateFolderRequest req = new CreateFolderRequest("New Root", null, null);
        Folder firstSave = Folder.builder().id(10L).name("New Root").path("/").createdBy(99L).build();
        when(folderRepository.save(any())).thenReturn(firstSave);
        FolderDto dto = new FolderDto(10L, "New Root", null, "/10/", null, false, null, 99L, null, null);
        when(folderMapper.toDto(any())).thenReturn(dto);

        folderService.createFolder(req, 99L);

        ArgumentCaptor<Folder> captor = ArgumentCaptor.forClass(Folder.class);
        verify(folderRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getPath()).isEqualTo("/10/");
    }

    @Test
    void createChildFolder_appendsParentPath() {
        CreateFolderRequest req = new CreateFolderRequest("Child", 1L, null);
        when(folderRepository.findById(1L)).thenReturn(Optional.of(rootFolder));
        Folder firstSave = Folder.builder().id(5L).name("Child").parentId(1L).path("/").createdBy(99L).build();
        when(folderRepository.save(any())).thenReturn(firstSave);
        when(folderMapper.toDto(any())).thenReturn(
                new FolderDto(5L, "Child", 1L, "/1/5/", null, false, null, 99L, null, null));

        folderService.createFolder(req, 99L);

        ArgumentCaptor<Folder> captor = ArgumentCaptor.forClass(Folder.class);
        verify(folderRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getPath()).isEqualTo("/1/5/");
    }

    @Test
    void createChildFolder_parentNotFound_throws() {
        when(folderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> folderService.createFolder(new CreateFolderRequest("X", 99L, null), 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getFolder ---

    @Test
    void getFolder_returnsDto() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(rootFolder));
        FolderDto dto = new FolderDto(1L, "Root", null, "/1/", null, false, null, 99L, null, null);
        when(folderMapper.toDto(rootFolder)).thenReturn(dto);

        assertThat(folderService.getFolder(1L).id()).isEqualTo(1L);
    }

    @Test
    void getFolder_notFound_throws() {
        when(folderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> folderService.getFolder(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- listChildren ---

    @Test
    void listChildren_returnsNonTrashedChildren() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(rootFolder));
        when(aclService.hasPermission(99L, 1L, AclResourceType.FOLDER, AclPermission.READ)).thenReturn(true);
        when(folderRepository.findByParentIdAndTrashedFalse(1L)).thenReturn(List.of(childFolder));
        FolderDto dto = new FolderDto(2L, "Child", 1L, "/1/2/", null, false, null, 99L, null, null);
        when(folderMapper.toDto(childFolder)).thenReturn(dto);

        List<FolderDto> result = folderService.listChildren(1L, 99L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(2L);
    }

    // --- updateFolder ---

    @Test
    void updateFolder_updatesNameAndDescription() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(rootFolder));
        when(folderRepository.save(any())).thenReturn(rootFolder);
        when(folderMapper.toDto(any())).thenReturn(
                new FolderDto(1L, "Renamed", null, "/1/", "desc", false, null, 99L, null, null));

        folderService.updateFolder(1L, new UpdateFolderRequest("Renamed", "desc"));

        verify(folderRepository).save(argThat(f ->
                "Renamed".equals(f.getName()) && "desc".equals(f.getDescription())));
    }

    // --- moveFolder ---

    @Test
    @SuppressWarnings("unchecked")
    void moveFolder_updatesPathAndDescendants() {
        Folder newParent = Folder.builder().id(10L).name("NP").path("/10/").createdBy(99L).build();
        when(folderRepository.findById(2L)).thenReturn(Optional.of(childFolder));
        when(folderRepository.findById(10L)).thenReturn(Optional.of(newParent));
        when(folderRepository.findByPathStartingWith("/1/2/"))
                .thenReturn(List.of(childFolder, grandchildFolder));
        when(folderRepository.saveAll(any())).thenReturn(List.of());
        when(folderMapper.toDto(childFolder)).thenReturn(
                new FolderDto(2L, "Child", 10L, "/10/2/", null, false, null, 99L, null, null));

        folderService.moveFolder(2L, 10L);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(folderRepository).saveAll(captor.capture());
        List<Folder> saved = captor.getValue();
        Folder movedFolder = saved.stream().filter(f -> f.getId().equals(2L)).findFirst().orElseThrow();
        Folder movedGrandchild = saved.stream().filter(f -> f.getId().equals(3L)).findFirst().orElseThrow();
        assertThat(movedFolder.getParentId()).isEqualTo(10L);
        assertThat(movedFolder.getPath()).isEqualTo("/10/2/");
        assertThat(movedGrandchild.getPath()).isEqualTo("/10/2/3/");
    }

    @Test
    void moveFolder_intoOwnSubtree_throws() {
        // Moving rootFolder (path=/1/) under childFolder (path=/1/2/) would create a cycle
        when(folderRepository.findById(1L)).thenReturn(Optional.of(rootFolder));
        when(folderRepository.findById(2L)).thenReturn(Optional.of(childFolder));

        assertThatThrownBy(() -> folderService.moveFolder(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    // --- trashFolder ---

    @Test
    @SuppressWarnings("unchecked")
    void trashFolder_marksAllDescendantsAsTrashed() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(rootFolder));
        when(folderRepository.findByPathStartingWith("/1/"))
                .thenReturn(List.of(rootFolder, childFolder, grandchildFolder));
        when(folderRepository.saveAll(any())).thenReturn(List.of());

        folderService.trashFolder(1L);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(folderRepository).saveAll(captor.capture());
        assertThat((List<Folder>) captor.getValue()).allSatisfy(f -> {
            assertThat(f.isTrashed()).isTrue();
            assertThat(f.getTrashedAt()).isNotNull();
        });
    }

    // --- restoreFolder ---

    @Test
    @SuppressWarnings("unchecked")
    void restoreFolder_clearsTrashFlag() {
        Folder trashed = Folder.builder().id(1L).name("Root").path("/1/").trashed(true).createdBy(99L).build();
        when(folderRepository.findById(1L)).thenReturn(Optional.of(trashed));
        when(folderRepository.findByPathStartingWith("/1/")).thenReturn(List.of(trashed));
        when(folderRepository.saveAll(any())).thenReturn(List.of(trashed));
        when(folderMapper.toDto(trashed)).thenReturn(
                new FolderDto(1L, "Root", null, "/1/", null, false, null, 99L, null, null));

        folderService.restoreFolder(1L);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(folderRepository).saveAll(captor.capture());
        assertThat((List<Folder>) captor.getValue()).allSatisfy(f -> {
            assertThat(f.isTrashed()).isFalse();
            assertThat(f.getTrashedAt()).isNull();
        });
    }

    // --- purgeFolder ---

    @Test
    void purgeFolder_deletesFolder() {
        Folder trashed = Folder.builder().id(1L).name("Root").path("/1/").trashed(true).createdBy(99L).build();
        when(folderRepository.findById(1L)).thenReturn(Optional.of(trashed));
        when(folderRepository.findByPathStartingWith("/1/")).thenReturn(List.of(trashed));

        folderService.purgeFolder(1L);

        verify(aclEntryRepository).deleteByResourceTypeAndResourceIdIn(AclResourceType.FOLDER, List.of(1L));
        verify(folderRepository).delete(trashed);
    }

    @Test
    void purgeFolder_notTrashed_throws() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(rootFolder));
        assertThatThrownBy(() -> folderService.purgeFolder(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- getBreadcrumb ---

    @Test
    void getBreadcrumb_returnsAncestorsInPathOrder() {
        when(folderRepository.findById(3L)).thenReturn(Optional.of(grandchildFolder));
        when(aclService.hasPermission(99L, 3L, AclResourceType.FOLDER, AclPermission.READ)).thenReturn(true);
        // DB may return in any order — service must sort by path position
        when(folderRepository.findAllById(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(grandchildFolder, rootFolder, childFolder));
        when(folderMapper.toBreadcrumbItem(rootFolder)).thenReturn(new BreadcrumbItemDto(1L, "Root"));
        when(folderMapper.toBreadcrumbItem(childFolder)).thenReturn(new BreadcrumbItemDto(2L, "Child"));
        when(folderMapper.toBreadcrumbItem(grandchildFolder)).thenReturn(new BreadcrumbItemDto(3L, "Grandchild"));

        List<BreadcrumbItemDto> result = folderService.getBreadcrumb(3L, 99L);

        assertThat(result).extracting(BreadcrumbItemDto::id).containsExactly(1L, 2L, 3L);
    }

    // --- favorites ---

    @Test
    void addFavorite_savesFavorite() {
        when(favoriteRepository.existsById(new FolderFavoriteId(5L, 1L))).thenReturn(false);
        when(folderRepository.findById(1L)).thenReturn(Optional.of(rootFolder));

        folderService.addFavorite(1L, 5L);

        verify(favoriteRepository).save(any(FolderFavorite.class));
    }

    @Test
    void addFavorite_alreadyExists_doesNotDuplicate() {
        when(favoriteRepository.existsById(new FolderFavoriteId(5L, 1L))).thenReturn(true);

        folderService.addFavorite(1L, 5L);

        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void removeFavorite_deletesFavorite() {
        folderService.removeFavorite(1L, 5L);
        verify(favoriteRepository).deleteById(new FolderFavoriteId(5L, 1L));
    }

    @Test
    void getFavorites_returnsFolders() {
        when(favoriteRepository.findFolderIdsByUserId(5L)).thenReturn(List.of(1L));
        when(folderRepository.findAllById(List.of(1L))).thenReturn(List.of(rootFolder));
        FolderDto dto = new FolderDto(1L, "Root", null, "/1/", null, false, null, 99L, null, null);
        when(folderMapper.toDto(rootFolder)).thenReturn(dto);

        List<FolderDto> result = folderService.getFavorites(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    // --- parsePathIds ---

    @Test
    void parsePathIds_rootPath_empty() {
        assertThat(folderService.parsePathIds("/")).isEmpty();
    }

    @Test
    void parsePathIds_singleSegment() {
        assertThat(folderService.parsePathIds("/5/")).containsExactly(5L);
    }

    @Test
    void parsePathIds_deepPath() {
        assertThat(folderService.parsePathIds("/1/2/3/")).containsExactly(1L, 2L, 3L);
    }
}
