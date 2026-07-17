package de.toengi.cili.service;

import de.toengi.cili.dto.bulkimport.BulkImportEntry;
import de.toengi.cili.dto.bulkimport.BulkImportJobDto;
import de.toengi.cili.dto.bulkimport.CreateBulkImportRequest;
import de.toengi.cili.dto.bulkimport.CreateBulkImportResponse;
import de.toengi.cili.dto.folder.CreateFolderRequest;
import de.toengi.cili.dto.folder.FolderDto;
import de.toengi.cili.model.entity.BulkImportItem;
import de.toengi.cili.model.entity.BulkImportJob;
import de.toengi.cili.model.entity.Folder;
import de.toengi.cili.model.enums.BulkImportItemStatus;
import de.toengi.cili.repository.BulkImportItemRepository;
import de.toengi.cili.repository.BulkImportJobRepository;
import de.toengi.cili.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkImportServiceTest {

    @Mock BulkImportJobRepository bulkImportJobRepository;
    @Mock BulkImportItemRepository bulkImportItemRepository;
    @Mock FolderRepository folderRepository;
    @Mock FolderService folderService;
    @Mock TextExtractionService textExtractionService;

    BulkImportService bulkImportService;

    @BeforeEach
    void setUp() {
        bulkImportService = new BulkImportService(
                bulkImportJobRepository, bulkImportItemRepository, folderRepository, folderService,
                textExtractionService);
        lenient().when(bulkImportJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(bulkImportItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createImport_reusesExistingFolder_doesNotCreateDuplicate() {
        when(folderRepository.findByParentIdAndName(10L, "Interviews"))
                .thenReturn(List.of(Folder.builder().id(99L).name("Interviews").parentId(10L).build()));

        CreateBulkImportRequest req = new CreateBulkImportRequest(10L, "Kampagne", List.of(
                new BulkImportEntry("Interviews/video1.mp4", 1000L, "video/mp4", null)));

        CreateBulkImportResponse response = bulkImportService.createImport(req, 1L);

        verify(folderService, never()).createFolder(any(), any());
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).resolvedFolderId()).isEqualTo(99L);
        assertThat(response.items().get(0).status()).isEqualTo(BulkImportItemStatus.PENDING);
    }

    @Test
    void createImport_createsMissingFolder() {
        when(folderRepository.findByParentIdAndName(10L, "Interviews")).thenReturn(List.of());
        when(folderService.createFolder(eq(new CreateFolderRequest("Interviews", 10L, null)), eq(1L)))
                .thenReturn(new FolderDto(100L, "Interviews", 10L, "/10/100/", null, false, null, 1L,
                        LocalDateTime.now(), LocalDateTime.now()));

        CreateBulkImportRequest req = new CreateBulkImportRequest(10L, "Kampagne", List.of(
                new BulkImportEntry("Interviews/video1.mp4", 1000L, "video/mp4", null)));

        CreateBulkImportResponse response = bulkImportService.createImport(req, 1L);

        verify(folderService, times(1)).createFolder(any(), any());
        assertThat(response.items().get(0).resolvedFolderId()).isEqualTo(100L);
    }

    @Test
    void createImport_multipleFilesSameDir_resolvesFolderOnce() {
        when(folderRepository.findByParentIdAndName(10L, "Interviews"))
                .thenReturn(List.of(Folder.builder().id(99L).name("Interviews").parentId(10L).build()));

        CreateBulkImportRequest req = new CreateBulkImportRequest(10L, "Kampagne", List.of(
                new BulkImportEntry("Interviews/video1.mp4", 1000L, "video/mp4", null),
                new BulkImportEntry("Interviews/video2.mp4", 2000L, "video/mp4", null),
                new BulkImportEntry("Interviews/video3.mp4", 3000L, "video/mp4", null)));

        bulkImportService.createImport(req, 1L);

        verify(folderRepository, times(1)).findByParentIdAndName(10L, "Interviews");
    }

    @Test
    void createImport_unsupportedMimeType_skipped() {
        CreateBulkImportRequest req = new CreateBulkImportRequest(10L, "Kampagne", List.of(
                new BulkImportEntry("notes.exe", 500L, "application/x-msdownload", null)));

        CreateBulkImportResponse response = bulkImportService.createImport(req, 1L);

        assertThat(response.items().get(0).status()).isEqualTo(BulkImportItemStatus.SKIPPED);
        assertThat(response.items().get(0).skipReason()).contains("application/x-msdownload");
        verifyNoInteractions(folderRepository); // Datei liegt im Root des Zielordners, kein Unterordner nötig
    }

    @Test
    void createImport_srtWithGenericMime_normalizedAndAccepted() {
        // Browser melden für .srt oft "application/octet-stream" oder gar nichts —
        // UploadService.normalizeMimeType() erkennt anhand der Endung "text/plain".
        CreateBulkImportRequest req = new CreateBulkImportRequest(10L, "Kampagne", List.of(
                new BulkImportEntry("video1.srt", 200L, "application/octet-stream", null)));

        CreateBulkImportResponse response = bulkImportService.createImport(req, 1L);

        assertThat(response.items().get(0).status()).isEqualTo(BulkImportItemStatus.PENDING);
    }

    @Test
    void createImport_videoWithGenericMime_fallsBackToExtension() {
        // Browser melden für viele Video-Container (z.B. .mkv) oft "application/octet-stream" —
        // normalizeMimeType() kennt nur Textformate, daher greift hier der Endungs-Fallback.
        CreateBulkImportRequest req = new CreateBulkImportRequest(10L, "Kampagne", List.of(
                new BulkImportEntry("video1.mkv", 5000L, "application/octet-stream", null)));

        CreateBulkImportResponse response = bulkImportService.createImport(req, 1L);

        assertThat(response.items().get(0).status()).isEqualTo(BulkImportItemStatus.PENDING);
    }

    @Test
    void createImport_deeplyNestedPath_resolvesIntermediateFolderWithoutOwnFile() {
        // "Season1" enthält selbst keine Datei direkt, nur der Unterordner "Episode1" tut es —
        // ohne vollständige Vorfahren-Expansion in resolveFolderTree würde "Episode1" fälschlich
        // als Root-Ordner (parentId=null) statt unterhalb von "Season1" angelegt.
        when(folderRepository.findByParentIdAndName(10L, "Season1")).thenReturn(List.of());
        when(folderService.createFolder(eq(new CreateFolderRequest("Season1", 10L, null)), eq(1L)))
                .thenReturn(new FolderDto(200L, "Season1", 10L, "/10/200/", null, false, null, 1L,
                        LocalDateTime.now(), LocalDateTime.now()));
        when(folderRepository.findByParentIdAndName(200L, "Episode1")).thenReturn(List.of());
        when(folderService.createFolder(eq(new CreateFolderRequest("Episode1", 200L, null)), eq(1L)))
                .thenReturn(new FolderDto(201L, "Episode1", 200L, "/10/200/201/", null, false, null, 1L,
                        LocalDateTime.now(), LocalDateTime.now()));

        CreateBulkImportRequest req = new CreateBulkImportRequest(10L, "Kampagne", List.of(
                new BulkImportEntry("Season1/Episode1/video.mp4", 1000L, "video/mp4", null)));

        CreateBulkImportResponse response = bulkImportService.createImport(req, 1L);

        verify(folderService).createFolder(eq(new CreateFolderRequest("Season1", 10L, null)), eq(1L));
        verify(folderService).createFolder(eq(new CreateFolderRequest("Episode1", 200L, null)), eq(1L));
        assertThat(response.items().get(0).resolvedFolderId()).isEqualTo(201L);
    }

    @Test
    void getJob_derivesRunningStatus_whenNotAllItemsResolved() {
        BulkImportJob job = BulkImportJob.builder().id("j1").adminUserId(1L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(3).filesDone(1).filesSkipped(0).filesFailed(0).build();
        when(bulkImportJobRepository.findById("j1")).thenReturn(java.util.Optional.of(job));
        when(bulkImportItemRepository.findByBulkImportJobId("j1")).thenReturn(List.of());

        BulkImportJobDto dto = bulkImportService.getJob("j1", 1L);

        assertThat(dto.status()).isEqualTo("RUNNING");
    }

    @Test
    void getJob_derivesCompletedStatus_whenAllDoneNoneFailed() {
        BulkImportJob job = BulkImportJob.builder().id("j2").adminUserId(1L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(3).filesDone(2).filesSkipped(1).filesFailed(0).build();
        when(bulkImportJobRepository.findById("j2")).thenReturn(java.util.Optional.of(job));
        when(bulkImportItemRepository.findByBulkImportJobId("j2")).thenReturn(List.of());

        BulkImportJobDto dto = bulkImportService.getJob("j2", 1L);

        assertThat(dto.status()).isEqualTo("COMPLETED");
    }

    @Test
    void getJob_derivesCompletedWithErrorsStatus_whenSomeFailed() {
        BulkImportJob job = BulkImportJob.builder().id("j3").adminUserId(1L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(3).filesDone(1).filesSkipped(1).filesFailed(1).build();
        when(bulkImportJobRepository.findById("j3")).thenReturn(java.util.Optional.of(job));
        when(bulkImportItemRepository.findByBulkImportJobId("j3")).thenReturn(List.of());

        BulkImportJobDto dto = bulkImportService.getJob("j3", 1L);

        assertThat(dto.status()).isEqualTo("COMPLETED_WITH_ERRORS");
    }

    @Test
    void getJob_wrongAdmin_throwsAccessDenied() {
        BulkImportJob job = BulkImportJob.builder().id("j4").adminUserId(99L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(1).build();
        when(bulkImportJobRepository.findById("j4")).thenReturn(java.util.Optional.of(job));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bulkImportService.getJob("j4", 1L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void failItem_wrongAdmin_throwsAccessDenied() {
        // Spiegelt getJob_wrongAdmin_throwsAccessDenied — ohne diesen Guard könnte jeder
        // Admin per erratener jobId/itemId fremde Items als FAILED markieren (s. Spec).
        BulkImportJob job = BulkImportJob.builder().id("j4b").adminUserId(99L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(1).build();
        when(bulkImportJobRepository.findById("j4b")).thenReturn(java.util.Optional.of(job));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bulkImportService.failItem("j4b", 1L, "x", 1L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(bulkImportItemRepository, never()).findById(any());
    }

    @Test
    void failItem_setsFailedAndIncrementsCounter() {
        BulkImportJob job = BulkImportJob.builder().id("j5").adminUserId(1L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(1).build();
        BulkImportItem item = BulkImportItem.builder().id(1L).bulkImportJobId("j5")
                .relativePath("video1.mp4").fileSize(100L).status(BulkImportItemStatus.UPLOADING).build();
        when(bulkImportJobRepository.findById("j5")).thenReturn(java.util.Optional.of(job));
        when(bulkImportItemRepository.findById(1L)).thenReturn(java.util.Optional.of(item));

        bulkImportService.failItem("j5", 1L, "Netzwerkfehler", 1L);

        assertThat(item.getStatus()).isEqualTo(BulkImportItemStatus.FAILED);
        assertThat(item.getErrorMessage()).isEqualTo("Netzwerkfehler");
        verify(bulkImportJobRepository).incrementFilesFailed("j5");
    }

    @Test
    void failItem_itemBelongsToDifferentJob_throwsNotFound() {
        // jobId ownership alone is not enough: "j7" genuinely belongs to actorUserId=1L, but the
        // fetched item's own bulkImportJobId is "someOtherJob" — an admin must not be able to
        // mutate/mark-failed an item that was never part of the job named in the path, even if
        // they legitimately own that path's job. See BulkImportService.failItem for the guard.
        BulkImportJob job = BulkImportJob.builder().id("j7").adminUserId(1L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(1).build();
        BulkImportItem item = BulkImportItem.builder().id(3L).bulkImportJobId("someOtherJob")
                .relativePath("video1.mp4").fileSize(100L).status(BulkImportItemStatus.UPLOADING).build();
        when(bulkImportJobRepository.findById("j7")).thenReturn(java.util.Optional.of(job));
        when(bulkImportItemRepository.findById(3L)).thenReturn(java.util.Optional.of(item));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bulkImportService.failItem("j7", 3L, "x", 1L))
                .isInstanceOf(de.toengi.cili.exception.ResourceNotFoundException.class);

        assertThat(item.getStatus()).isEqualTo(BulkImportItemStatus.UPLOADING); // unverändert
        verify(bulkImportItemRepository, never()).save(any());
        verify(bulkImportJobRepository, never()).incrementFilesFailed(any());
    }

    @Test
    void failItem_alreadyTerminal_noOp() {
        BulkImportJob job = BulkImportJob.builder().id("j6").adminUserId(1L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(1).build();
        BulkImportItem item = BulkImportItem.builder().id(2L).bulkImportJobId("j6")
                .relativePath("video1.mp4").fileSize(100L).status(BulkImportItemStatus.DONE).build();
        when(bulkImportJobRepository.findById("j6")).thenReturn(java.util.Optional.of(job));
        when(bulkImportItemRepository.findById(2L)).thenReturn(java.util.Optional.of(item));

        bulkImportService.failItem("j6", 2L, "zu spät", 1L);

        assertThat(item.getStatus()).isEqualTo(BulkImportItemStatus.DONE); // unverändert
        verify(bulkImportJobRepository, never()).incrementFilesFailed(any());
    }
}
