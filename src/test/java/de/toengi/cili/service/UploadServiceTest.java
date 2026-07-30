package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.dto.upload.CompleteUploadResponse;
import de.toengi.cili.dto.upload.InitUploadRequest;
import de.toengi.cili.dto.upload.UploadJobDto;
import de.toengi.cili.event.ResourceUploadedEvent;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.BulkImportItem;
import de.toengi.cili.model.entity.BulkImportJob;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.UploadJob;
import de.toengi.cili.model.enums.BulkImportItemStatus;
import de.toengi.cili.model.enums.UploadJobStatus;
import de.toengi.cili.repository.BulkImportItemRepository;
import de.toengi.cili.repository.BulkImportJobRepository;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.UploadJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    UploadJobRepository uploadJobRepository;
    @Mock
    ResourceRepository resourceRepository;
    @Mock
    ResourceMetadataRepository metadataRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    PlatformTransactionManager txManager;
    @Mock
    BulkImportItemRepository bulkImportItemRepository;
    @Mock
    BulkImportJobRepository bulkImportJobRepository;

    UploadService uploadService;

    @BeforeEach
    void setUp() {
        FileStorageConfig config = new FileStorageConfig();
        config.setBasePath(tempDir.toString());
        lenient().when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        uploadService = new UploadService(uploadJobRepository, resourceRepository, metadataRepository, config,
                eventPublisher, txManager, bulkImportItemRepository, bulkImportJobRepository);
    }

    // --- initUpload ---

    @Test
    void initUpload_rejects_html_mime_type() {
        InitUploadRequest req = new InitUploadRequest("evil.html", "text/html", 100L, 1000, 10L, null, null, null);

        assertThatThrownBy(() -> uploadService.initUpload(req, 1L))
                .isInstanceOf(CiliException.class)
                .hasMessageContaining("text/html");
    }

    @Test
    void initUpload_rejects_javascript_mime_type() {
        InitUploadRequest req = new InitUploadRequest("script.js", "application/javascript", 100L, 1000, 10L, null, null, null);

        assertThatThrownBy(() -> uploadService.initUpload(req, 1L))
                .isInstanceOf(CiliException.class);
    }

    @Test
    void initUpload_rejects_svg_mime_type() {
        InitUploadRequest req = new InitUploadRequest("image.svg", "image/svg+xml", 100L, 1000, 10L, null, null, null);

        assertThatThrownBy(() -> uploadService.initUpload(req, 1L))
                .isInstanceOf(CiliException.class);
    }

    @Test
    void initUpload_singleChunk_chunksTotal1() {
        InitUploadRequest req = new InitUploadRequest("video.mp4", "video/mp4", 500L, 1000, 10L, null, null, null);
        when(uploadJobRepository.save(any(UploadJob.class))).thenAnswer(inv -> inv.getArgument(0));

        UploadJobDto dto = uploadService.initUpload(req, 1L);

        assertThat(dto.chunksTotal()).isEqualTo(1);
        assertThat(dto.chunksReceived()).isEqualTo(0);
        assertThat(dto.status()).isEqualTo(UploadJobStatus.INITIATED);
        assertThat(dto.jobId()).isNotNull().hasSize(36);
    }

    @Test
    void initUpload_partialLastChunk_roundsUp() {
        InitUploadRequest req = new InitUploadRequest("big.mp4", "video/mp4", 2500L, 1000, 10L, null, null, null);
        when(uploadJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UploadJobDto dto = uploadService.initUpload(req, 1L);

        assertThat(dto.chunksTotal()).isEqualTo(3); // ceil(2500/1000) = 3
    }

    // --- uploadChunk ---

    @Test
    void uploadChunk_writesChunkToDisk() throws IOException {
        UploadJob job = job("j1", 1L, 10L, 3, 1000);
        when(uploadJobRepository.findById("j1")).thenReturn(Optional.of(job));
        when(uploadJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        byte[] data = "chunk0data".getBytes();
        uploadService.uploadChunk("j1", 0, new ByteArrayInputStream(data), 1L);

        Path chunkFile = tempDir.resolve("uploads/j1/0");
        assertThat(chunkFile).exists();
        assertThat(Files.readAllBytes(chunkFile)).isEqualTo(data);
    }

    @Test
    void uploadChunk_wrongOwner_throwsAccessDenied() {
        UploadJob job = job("j2", 99L, 10L, 3, 1000);
        when(uploadJobRepository.findById("j2")).thenReturn(Optional.of(job));

        assertThatThrownBy(() ->
                uploadService.uploadChunk("j2", 0, new ByteArrayInputStream(new byte[0]), 1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void uploadChunk_invalidIndex_throwsIllegalArgument() {
        UploadJob job = job("j3", 1L, 10L, 3, 1000);
        when(uploadJobRepository.findById("j3")).thenReturn(Optional.of(job));

        assertThatThrownBy(() ->
                uploadService.uploadChunk("j3", 5, new ByteArrayInputStream(new byte[0]), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uploadChunk_jobNotFound_throws() {
        when(uploadJobRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                uploadService.uploadChunk("missing", 0, new ByteArrayInputStream(new byte[0]), 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- completeUpload ---

    @Test
    void completeUpload_allChunksPresent_createsResourceAndFiresEvent() throws IOException {
        UploadJob job = job("j4", 1L, 10L, 2, 5);
        job.setChunksReceived(2);
        when(uploadJobRepository.findById("j4")).thenReturn(Optional.of(job));
        when(uploadJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Path chunkDir = tempDir.resolve("uploads/j4");
        Files.createDirectories(chunkDir);
        Files.write(chunkDir.resolve("0"), "hello".getBytes());
        Files.write(chunkDir.resolve("1"), "world".getBytes());

        Resource saved = Resource.builder().id(42L).folderId(10L)
                .storedName("some-uuid").mimeType("video/mp4").size(10L).build();
        when(resourceRepository.save(any())).thenReturn(saved);
        lenient().when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompleteUploadResponse response = uploadService.completeUpload("j4", 1L, false);

        assertThat(response.resourceId()).isEqualTo(42L);
        verify(eventPublisher).publishEvent(any(ResourceUploadedEvent.class));
        assertThat(chunkDir).doesNotExist();
    }

    @Test
    void completeUpload_missingChunk_throwsIllegalState() throws IOException {
        UploadJob job = job("j5", 1L, 10L, 2, 5);
        when(uploadJobRepository.findById("j5")).thenReturn(Optional.of(job));

        Path chunkDir = tempDir.resolve("uploads/j5");
        Files.createDirectories(chunkDir);
        Files.write(chunkDir.resolve("0"), "hello".getBytes());
        // chunk 1 missing

        assertThatThrownBy(() -> uploadService.completeUpload("j5", 1L, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing chunk");
    }

    @Test
    void completeUpload_withBulkImportItemId_marksDoneAndIncrementsCounter() throws IOException {
        UploadJob job = job("j8", 1L, 10L, 1, 5);
        job.setBulkImportItemId(5L);
        job.setChunksReceived(1);
        when(uploadJobRepository.findById("j8")).thenReturn(Optional.of(job));
        when(uploadJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Path chunkDir = tempDir.resolve("uploads/j8");
        Files.createDirectories(chunkDir);
        Files.write(chunkDir.resolve("0"), "hello".getBytes());

        Resource saved = Resource.builder().id(42L).folderId(10L)
                .storedName("some-uuid").mimeType("video/mp4").size(5L).build();
        when(resourceRepository.save(any())).thenReturn(saved);
        lenient().when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BulkImportItem item = BulkImportItem.builder().id(5L).bulkImportJobId("bulkjob1")
                .relativePath("video1.mp4").fileSize(5L).status(BulkImportItemStatus.UPLOADING).build();
        when(bulkImportItemRepository.findById(5L)).thenReturn(Optional.of(item));
        // Simulates "this call won the race": the atomic conditional UPDATE affected 1 row.
        when(bulkImportItemRepository.markDoneIfNotAlreadyDone(5L, 42L)).thenReturn(1);

        uploadService.completeUpload("j8", 1L, false);

        assertThat(item.getStatus()).isEqualTo(BulkImportItemStatus.DONE);
        assertThat(item.getResourceId()).isEqualTo(42L);
        verify(bulkImportJobRepository).incrementFilesDone("bulkjob1");
    }

    @Test
    void completeUpload_bulkImportItemAlreadyDone_doesNotDoubleCountCompletion() throws IOException {
        UploadJob job = job("j9", 1L, 10L, 1, 5);
        job.setBulkImportItemId(6L);
        job.setChunksReceived(1);
        when(uploadJobRepository.findById("j9")).thenReturn(Optional.of(job));
        when(uploadJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Path chunkDir = tempDir.resolve("uploads/j9");
        Files.createDirectories(chunkDir);
        Files.write(chunkDir.resolve("0"), "hello".getBytes());

        Resource saved = Resource.builder().id(43L).folderId(10L)
                .storedName("some-uuid-2").mimeType("video/mp4").size(5L).build();
        when(resourceRepository.save(any())).thenReturn(saved);
        lenient().when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Item ist bereits DONE, z.B. weil ein zweiter (Resume-)UploadJob zuerst fertig wurde.
        // markDoneIfNotAlreadyDone is intentionally left unstubbed here: Mockito's default for
        // an unstubbed int-returning method is 0, which is exactly what the real atomic UPDATE
        // would also return for a row that no longer matches "status <> DONE" — i.e. this test
        // exercises the "0 rows affected" branch without needing an explicit stub.
        BulkImportItem item = BulkImportItem.builder().id(6L).bulkImportJobId("bulkjob2")
                .relativePath("video1.mp4").fileSize(5L).status(BulkImportItemStatus.DONE).resourceId(999L).build();
        when(bulkImportItemRepository.findById(6L)).thenReturn(Optional.of(item));

        uploadService.completeUpload("j9", 1L, false);

        assertThat(item.getResourceId()).isEqualTo(999L); // unverändert
        verify(bulkImportItemRepository).markDoneIfNotAlreadyDone(6L, 43L);
        verify(bulkImportJobRepository, never()).incrementFilesDone(any());
    }

    // --- cancelUpload ---

    @Test
    void cancelUpload_deletesChunksAndJob() throws IOException {
        UploadJob job = job("j6", 1L, 10L, 1, 100);
        when(uploadJobRepository.findById("j6")).thenReturn(Optional.of(job));

        Path chunkDir = tempDir.resolve("uploads/j6");
        Files.createDirectories(chunkDir);
        Files.write(chunkDir.resolve("0"), "data".getBytes());

        uploadService.cancelUpload("j6", 1L);

        assertThat(chunkDir).doesNotExist();
        verify(uploadJobRepository).delete(job);
    }

    // --- getStatus ---

    @Test
    void getStatus_returnsJobDto() {
        UploadJob job = job("j7", 1L, 10L, 3, 1000);
        job.setChunksReceived(1);
        job.setStatus(UploadJobStatus.IN_PROGRESS);
        when(uploadJobRepository.findById("j7")).thenReturn(Optional.of(job));

        UploadJobDto dto = uploadService.getStatus("j7", 1L);

        assertThat(dto.jobId()).isEqualTo("j7");
        assertThat(dto.chunksReceived()).isEqualTo(1);
        assertThat(dto.chunksTotal()).isEqualTo(3);
        assertThat(dto.status()).isEqualTo(UploadJobStatus.IN_PROGRESS);
    }

    @Test
    void getStatus_notFound_throws() {
        when(uploadJobRepository.findById("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> uploadService.getStatus("x", 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- initUpload mit bulkImportItemId ---

    @Test
    void initUpload_withBulkImportItemId_flipsItemToUploading() {
        BulkImportItem item = BulkImportItem.builder().id(5L).bulkImportJobId("job1")
                .relativePath("video1.mp4").fileSize(500L).status(BulkImportItemStatus.PENDING).build();
        BulkImportJob bulkJob = BulkImportJob.builder().id("job1").adminUserId(1L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(1).build();
        when(bulkImportItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(bulkImportJobRepository.findById("job1")).thenReturn(Optional.of(bulkJob));
        when(uploadJobRepository.save(any(UploadJob.class))).thenAnswer(inv -> inv.getArgument(0));

        InitUploadRequest req = new InitUploadRequest("video1.mp4", "video/mp4", 500L, 1000, 10L, null, null, 5L);
        uploadService.initUpload(req, 1L);

        assertThat(item.getStatus()).isEqualTo(BulkImportItemStatus.UPLOADING);
        verify(bulkImportItemRepository).save(item);
    }

    @Test
    void initUpload_bulkImportItemAlreadyDone_throwsCiliException() {
        BulkImportItem item = BulkImportItem.builder().id(6L).bulkImportJobId("job2")
                .relativePath("video1.mp4").fileSize(500L).status(BulkImportItemStatus.DONE).build();
        BulkImportJob bulkJob = BulkImportJob.builder().id("job2").adminUserId(1L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(1).build();
        when(bulkImportItemRepository.findById(6L)).thenReturn(Optional.of(item));
        when(bulkImportJobRepository.findById("job2")).thenReturn(Optional.of(bulkJob));

        InitUploadRequest req = new InitUploadRequest("video1.mp4", "video/mp4", 500L, 1000, 10L, null, null, 6L);

        assertThatThrownBy(() -> uploadService.initUpload(req, 1L))
                .isInstanceOf(CiliException.class);
    }

    @Test
    void initUpload_bulkImportItemWrongAdmin_throwsAccessDenied() {
        BulkImportItem item = BulkImportItem.builder().id(7L).bulkImportJobId("job3")
                .relativePath("video1.mp4").fileSize(500L).status(BulkImportItemStatus.PENDING).build();
        BulkImportJob bulkJob = BulkImportJob.builder().id("job3").adminUserId(99L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(1).build();
        when(bulkImportItemRepository.findById(7L)).thenReturn(Optional.of(item));
        when(bulkImportJobRepository.findById("job3")).thenReturn(Optional.of(bulkJob));

        InitUploadRequest req = new InitUploadRequest("video1.mp4", "video/mp4", 500L, 1000, 10L, null, null, 7L);

        assertThatThrownBy(() -> uploadService.initUpload(req, 1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void initUpload_bulkImportItemBlockedMimeType_marksItemFailedInsteadOfStuck() {
        // BulkImportService klassifiziert image/* pauschal als unterstützt (Chunk 2),
        // aber initUpload blockt image/svg+xml über BLOCKED_MIME_TYPES (bestehendes
        // Verhalten, s. initUpload_rejects_svg_mime_type oben) — ohne diesen Fail-Pfad
        // bliebe das Item für immer bei PENDING/UPLOADING hängen und der Job nie fertig.
        //
        // IMPORTANT — this test does NOT prove markBulkItemFailed()'s REQUIRES_NEW commit
        // boundary actually works. txManager here is a plain Mockito mock returning a fake
        // SimpleTransactionStatus with no real begin/commit/rollback semantics, so this test
        // passes purely because the entity/mock state is mutated in-process — it would pass
        // identically even if markBulkItemFailed() ran inline in the same (real) transaction
        // as before the fix, which is precisely the bug this change addresses. Proving the
        // fix actually survives a real rollback requires a @SpringBootTest-level test with a
        // genuine PlatformTransactionManager/JpaTransactionManager and an assertion that
        // re-reads the BulkImportItem from a fresh transaction after initUpload() returns
        // (its exception). That belongs with the Chunk 4 integration test suite once the
        // controller/persistence wiring exists — not added here to avoid faking coverage this
        // unit test structurally cannot provide.
        BulkImportItem item = BulkImportItem.builder().id(8L).bulkImportJobId("job4")
                .relativePath("diagram.svg").fileSize(100L).status(BulkImportItemStatus.PENDING).build();
        BulkImportJob bulkJob = BulkImportJob.builder().id("job4").adminUserId(1L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(1).build();
        when(bulkImportItemRepository.findById(8L)).thenReturn(Optional.of(item));
        when(bulkImportJobRepository.findById("job4")).thenReturn(Optional.of(bulkJob));

        InitUploadRequest req = new InitUploadRequest("diagram.svg", "image/svg+xml", 100L, 1000, 10L, null, null, 8L);

        assertThatThrownBy(() -> uploadService.initUpload(req, 1L))
                .isInstanceOf(CiliException.class);

        assertThat(item.getStatus()).isEqualTo(BulkImportItemStatus.FAILED);
        verify(bulkImportJobRepository).incrementFilesFailed("job4");
    }

    @Test
    void initUpload_bulkImportItemBlockedMimeType_retryDoesNotDoubleCountFailure() {
        // The frontend's upload hook (useBulkImportUpload.ts / uploadOne) retries initUpload on
        // ANY thrown error up to MAX_RETRIES times — including this deterministic 400 rejection
        // for a blocked MIME type. Without a terminal-state guard in markBulkItemFailed, each
        // retry would re-increment filesFailed for what is really a single failure, corrupting
        // the job's derived progress (resolved = filesDone + filesSkipped + filesFailed could
        // exceed filesTotal while other items are still PENDING/UPLOADING).
        BulkImportItem item = BulkImportItem.builder().id(8L).bulkImportJobId("job4")
                .relativePath("diagram.svg").fileSize(100L).status(BulkImportItemStatus.PENDING).build();
        BulkImportJob bulkJob = BulkImportJob.builder().id("job4").adminUserId(1L).targetFolderId(10L)
                .rootName("Kampagne").filesTotal(1).build();
        when(bulkImportItemRepository.findById(8L)).thenReturn(Optional.of(item));
        when(bulkImportJobRepository.findById("job4")).thenReturn(Optional.of(bulkJob));

        InitUploadRequest req = new InitUploadRequest("diagram.svg", "image/svg+xml", 100L, 1000, 10L, null, null, 8L);

        // First call: marks the item FAILED and increments the counter (same as the test above).
        assertThatThrownBy(() -> uploadService.initUpload(req, 1L))
                .isInstanceOf(CiliException.class);
        // Second call (frontend retry): findById returns the SAME item instance, now already
        // FAILED from the first call — must still reject with the same blocked-mime error, but
        // must NOT increment filesFailed again.
        assertThatThrownBy(() -> uploadService.initUpload(req, 1L))
                .isInstanceOf(CiliException.class);

        assertThat(item.getStatus()).isEqualTo(BulkImportItemStatus.FAILED);
        verify(bulkImportJobRepository, times(1)).incrementFilesFailed("job4");
    }

    @Test
    void normalizeMimeType_oggUploadedAsApplicationOgg_correctedToAudioOgg() {
        assertThat(UploadService.normalizeMimeType("audio_2026-07-29_16-57-53.ogg", "application/ogg"))
                .isEqualTo("audio/ogg");
        assertThat(UploadService.normalizeMimeType("recording.oga", "application/ogg"))
                .isEqualTo("audio/ogg");
    }

    // --- helper ---

    private UploadJob job(String id, Long userId, Long folderId, int chunksTotal, int chunkSize) {
        return UploadJob.builder()
                .id(id).userId(userId).folderId(folderId)
                .fileName("test.mp4").mimeType("video/mp4")
                .totalSize((long) chunksTotal * chunkSize)
                .chunkSize(chunkSize).chunksTotal(chunksTotal).chunksReceived(0)
                .status(UploadJobStatus.IN_PROGRESS)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
    }
}
