package de.toengi.cili.service;

import de.toengi.cili.config.FfmpegTranscodeConfig;
import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.event.ResourceUploadedEvent;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.AclResourceType;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaClipServiceTest {

    @Mock ResourceRepository resourceRepo;
    @Mock ResourceMetadataRepository metadataRepo;
    @Mock StorageService storageService;
    @Mock ProcessingJobService jobService;
    @Mock CommandRunner commandRunner;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock PlatformTransactionManager txManager;
    @Mock AclService aclService;
    @Mock ProcessingJobRepository jobRepository;
    FfmpegTranscodeConfig ffmpegConfig;
    FileStorageConfig storageConfig;
    MediaClipService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ffmpegConfig = new FfmpegTranscodeConfig();
        ffmpegConfig.setVideoCodec("libx264");
        ffmpegConfig.setAudioCodec("aac");
        ffmpegConfig.setCrf(23);
        ffmpegConfig.setPreset("medium");
        storageConfig = new FileStorageConfig();
        storageConfig.setFfmpegPath("/usr/bin/ffmpeg");
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new MediaClipService(
                resourceRepo, metadataRepo, storageService, jobService,
                commandRunner, ffmpegConfig, storageConfig, eventPublisher, txManager,
                aclService, jobRepository, Runnable::run);
    }

    @Test
    void buildClipCommand_containsSeekDurationAndCodec() {
        List<String> cmd = service.buildClipCommand(
                "video/mp4", Path.of("/input/video.mp4"), Path.of("/output/clip.mp4"),
                75_000L, 150_500L, ffmpegConfig);

        int ssIdx = cmd.indexOf("-ss");
        assertThat(ssIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(ssIdx + 1)).isEqualTo("75.000");

        int tIdx = cmd.indexOf("-t");
        assertThat(tIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(tIdx + 1)).isEqualTo("75.500");

        int vcodecIdx = cmd.indexOf("-vcodec");
        assertThat(cmd.get(vcodecIdx + 1)).isEqualTo("libx264");

        // -ss muss vor -i stehen (Input-Seeking, schnell + framegenau bei Re-Encode)
        assertThat(ssIdx).isLessThan(cmd.indexOf("-i"));
    }

    @Test
    void buildClipCommand_forAudioMimeType_producesMp3EncodeArgsWithoutVideoCodec() {
        List<String> cmd = service.buildClipCommand(
                "audio/mpeg", Path.of("/input/podcast.mp3"), Path.of("/output/clip.mp3"),
                75_000L, 150_500L, ffmpegConfig);

        assertThat(cmd).doesNotContain("-vcodec");
        assertThat(cmd).contains("-vn");

        int acodecIdx = cmd.indexOf("-acodec");
        assertThat(acodecIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(acodecIdx + 1)).isEqualTo("libmp3lame");

        int qscaleIdx = cmd.indexOf("-qscale:a");
        assertThat(qscaleIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(qscaleIdx + 1)).isEqualTo("2");

        int ssIdx = cmd.indexOf("-ss");
        assertThat(cmd.get(ssIdx + 1)).isEqualTo("75.000");
        int tIdx = cmd.indexOf("-t");
        assertThat(cmd.get(tIdx + 1)).isEqualTo("75.500");
    }

    @Test
    void execute_onFfmpegSuccess_createsNewResourceAndMarksDone() throws Exception {
        Resource source = Resource.builder().id(1L).storedName("source-uuid")
                .originalName("Urlaub.mp4").folderId(42L).uploaderId(7L)
                .mimeType("video/mp4").build();
        when(resourceRepo.findById(1L)).thenReturn(Optional.of(source));

        Path fakeInput = Files.createTempFile("input", ".mp4");
        when(storageService.resolveLocalPath("source-uuid")).thenReturn(Optional.of(fakeInput));

        Path tempDir = Files.createTempDirectory("cili-ffmpeg-clip");
        ffmpegConfig.setTempDir(tempDir.toString());

        when(commandRunner.run(anyList())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String outPath = cmd.get(cmd.size() - 1);
            Files.writeString(Path.of(outPath), "fake-clip-data");
            return 0;
        });
        when(storageService.resolveStoragePath(anyString())).thenAnswer(inv ->
                tempDir.resolve(inv.getArgument(0, String.class) + "-final"));
        when(resourceRepo.save(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(99L);
            return r;
        });

        ProcessingJob job = ProcessingJob.builder().id(5L).resourceId(1L)
                .attempts(0).maxAttempts(3)
                .result("{\"startMs\":75000,\"endMs\":150500}")
                .build();

        service.execute(job);

        verify(resourceRepo).save(argThat(r ->
                r.getOriginalName().equals("Urlaub_00-01-15_00-02-30.mp4")
                && r.getFolderId().equals(42L)));
        verify(eventPublisher).publishEvent(any(ResourceUploadedEvent.class));
        verify(jobService).markDone(eq(job), contains("\"newResourceId\":99"));

        Files.deleteIfExists(fakeInput);
    }

    @Test
    void execute_withCustomTitle_usesSanitizedTitleAsNameAndRawTitleAsMetadata() throws Exception {
        Resource source = Resource.builder().id(1L).storedName("source-uuid")
                .originalName("Urlaub.mp4").folderId(42L).uploaderId(7L)
                .mimeType("video/mp4").build();
        when(resourceRepo.findById(1L)).thenReturn(Optional.of(source));

        Path fakeInput = Files.createTempFile("input", ".mp4");
        when(storageService.resolveLocalPath("source-uuid")).thenReturn(Optional.of(fakeInput));

        Path tempDir = Files.createTempDirectory("cili-ffmpeg-clip");
        ffmpegConfig.setTempDir(tempDir.toString());

        when(commandRunner.run(anyList())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String outPath = cmd.get(cmd.size() - 1);
            Files.writeString(Path.of(outPath), "fake-clip-data");
            return 0;
        });
        when(storageService.resolveStoragePath(anyString())).thenAnswer(inv ->
                tempDir.resolve(inv.getArgument(0, String.class) + "-final"));
        when(resourceRepo.save(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(99L);
            return r;
        });

        ProcessingJob job = ProcessingJob.builder().id(5L).resourceId(1L)
                .attempts(0).maxAttempts(3)
                .result("{\"startMs\":75000,\"endMs\":150500,\"title\":\"  Best Moment: Sunset/Beach!  \"}")
                .build();

        service.execute(job);

        verify(resourceRepo).save(argThat(r ->
                r.getOriginalName().equals("Best_Moment__Sunset_Beach_.mp4")
                && r.getFolderId().equals(42L)));
        verify(metadataRepo).save(argThat(m ->
                m.getTitle().equals("Best Moment: Sunset/Beach!")));
        verify(jobService).markDone(eq(job), contains("\"newResourceId\":99"));

        Files.deleteIfExists(fakeInput);
    }

    @Test
    void execute_withBlankTitle_fallsBackToAutoGeneratedName() throws Exception {
        Resource source = Resource.builder().id(1L).storedName("source-uuid")
                .originalName("Urlaub.mp4").folderId(42L).uploaderId(7L)
                .mimeType("video/mp4").build();
        when(resourceRepo.findById(1L)).thenReturn(Optional.of(source));

        Path fakeInput = Files.createTempFile("input", ".mp4");
        when(storageService.resolveLocalPath("source-uuid")).thenReturn(Optional.of(fakeInput));

        Path tempDir = Files.createTempDirectory("cili-ffmpeg-clip");
        ffmpegConfig.setTempDir(tempDir.toString());

        when(commandRunner.run(anyList())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String outPath = cmd.get(cmd.size() - 1);
            Files.writeString(Path.of(outPath), "fake-clip-data");
            return 0;
        });
        when(storageService.resolveStoragePath(anyString())).thenAnswer(inv ->
                tempDir.resolve(inv.getArgument(0, String.class) + "-final"));
        when(resourceRepo.save(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(99L);
            return r;
        });

        ProcessingJob job = ProcessingJob.builder().id(5L).resourceId(1L)
                .attempts(0).maxAttempts(3)
                .result("{\"startMs\":75000,\"endMs\":150500,\"title\":\"   \"}")
                .build();

        service.execute(job);

        verify(resourceRepo).save(argThat(r ->
                r.getOriginalName().equals("Urlaub_00-01-15_00-02-30.mp4")
                && r.getFolderId().equals(42L)));
        verify(metadataRepo).save(argThat(m ->
                m.getTitle().equals("Urlaub_00-01-15_00-02-30")));
        verify(jobService).markDone(eq(job), contains("\"newResourceId\":99"));

        Files.deleteIfExists(fakeInput);
    }

    @Test
    void execute_onMetadataSaveFailure_rollsBackAndDeletesOrphanedFile() throws Exception {
        Resource source = Resource.builder().id(1L).storedName("source-uuid")
                .originalName("Urlaub.mp4").folderId(42L).uploaderId(7L)
                .mimeType("video/mp4").build();
        when(resourceRepo.findById(1L)).thenReturn(Optional.of(source));

        Path fakeInput = Files.createTempFile("input", ".mp4");
        when(storageService.resolveLocalPath("source-uuid")).thenReturn(Optional.of(fakeInput));

        Path tempDir = Files.createTempDirectory("cili-ffmpeg-clip");
        ffmpegConfig.setTempDir(tempDir.toString());

        when(commandRunner.run(anyList())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String outPath = cmd.get(cmd.size() - 1);
            Files.writeString(Path.of(outPath), "fake-clip-data");
            return 0;
        });
        when(storageService.resolveStoragePath(anyString())).thenAnswer(inv ->
                tempDir.resolve(inv.getArgument(0, String.class) + "-final"));
        when(resourceRepo.save(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(99L);
            return r;
        });
        when(metadataRepo.save(any())).thenThrow(new RuntimeException("boom"));

        ProcessingJob job = ProcessingJob.builder().id(5L).resourceId(1L)
                .attempts(0).maxAttempts(3)
                .result("{\"startMs\":75000,\"endMs\":150500}")
                .build();

        service.execute(job);

        // Framework-Ebene: die Transaktion wurde tatsächlich zurückgerollt, nicht committet
        verify(txManager).rollback(any());
        verify(txManager, never()).commit(any());
        // Die verwaiste Datei muss best-effort gelöscht werden (storedName == newUuid,
        // gleicher Wert, der auch an resolveStoragePath übergeben wurde)
        verify(storageService).delete(anyString());
        // Job darf NICHT als DONE markiert werden — die Transaktion ist fehlgeschlagen
        verify(jobService, never()).markDone(any(), anyString());
        verify(jobService).markFailed(eq(job), contains("boom"));

        Files.deleteIfExists(fakeInput);
    }

    @Test
    void execute_onEndMsNotAfterStartMs_marksFailedWithoutInvokingFfmpeg() throws Exception {
        Resource source = Resource.builder().id(1L).storedName("source-uuid")
                .originalName("Urlaub.mp4").folderId(42L).uploaderId(7L)
                .mimeType("video/mp4").build();
        // Kein Stubbing für resourceRepo/storageService nötig — die Validierung
        // muss VOR dem Laden der Resource bzw. dem FFmpeg-Aufruf greifen.

        ProcessingJob job = ProcessingJob.builder().id(5L).resourceId(1L)
                .attempts(0).maxAttempts(3)
                .result("{\"startMs\":5000,\"endMs\":5000}")
                .build();

        service.execute(job);

        verify(commandRunner, never()).run(anyList());
        verify(jobService, never()).markDone(any(), anyString());
        verify(jobService).markFailed(eq(job), contains("endMs"));
    }

    private static final List<ProcessingJobStatus> ACTIVE_STATUSES =
            List.of(ProcessingJobStatus.PENDING, ProcessingJobStatus.RUNNING);

    @Test
    void enqueueClip_happyPath_createsJobAndDispatchesToExecutor() throws Exception {
        Resource source = Resource.builder().id(1L).storedName("source-uuid")
                .originalName("Urlaub.mp4").folderId(42L).uploaderId(7L)
                .mimeType("video/mp4").build();
        when(resourceRepo.findById(1L)).thenReturn(Optional.of(source));
        when(aclService.hasPermission(9L, 42L, AclResourceType.FOLDER, AclPermission.UPLOAD)).thenReturn(true);
        when(jobRepository.findByResourceIdAndTypeAndStatusIn(1L, ProcessingJobType.VIDEO_CLIP, ACTIVE_STATUSES))
                .thenReturn(List.of());

        ProcessingJob createdJob = ProcessingJob.builder().id(10L).resourceId(1L)
                .attempts(0).maxAttempts(3)
                .result("{\"startMs\":75000,\"endMs\":150500}")
                .build();
        when(jobService.createJob(eq(1L), eq(ProcessingJobType.VIDEO_CLIP), isNull(), anyString()))
                .thenReturn(createdJob);

        Path fakeInput = Files.createTempFile("input", ".mp4");
        when(storageService.resolveLocalPath("source-uuid")).thenReturn(Optional.of(fakeInput));
        Path tempDir = Files.createTempDirectory("cili-ffmpeg-clip");
        ffmpegConfig.setTempDir(tempDir.toString());
        when(commandRunner.run(anyList())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String outPath = cmd.get(cmd.size() - 1);
            Files.writeString(Path.of(outPath), "fake-clip-data");
            return 0;
        });
        when(storageService.resolveStoragePath(anyString())).thenAnswer(inv ->
                tempDir.resolve(inv.getArgument(0, String.class) + "-final"));
        when(resourceRepo.save(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(99L);
            return r;
        });

        long jobId = service.enqueueClip(1L, 75_000L, 150_500L, null, 9L);

        assertThat(jobId).isEqualTo(10L);
        verify(jobService).createJob(eq(1L), eq(ProcessingJobType.VIDEO_CLIP), isNull(), anyString());
        // Runnable::run as test executor runs execute() synchronously — proves the job was dispatched
        verify(commandRunner).run(anyList());
        verify(jobService).markDone(eq(createdJob), anyString());

        Files.deleteIfExists(fakeInput);
    }

    @Test
    void enqueueClip_whenUploadPermissionDenied_throwsAccessDeniedAndNeverCreatesJob() {
        Resource source = Resource.builder().id(1L).storedName("source-uuid")
                .originalName("Urlaub.mp4").folderId(42L).uploaderId(7L)
                .mimeType("video/mp4").build();
        when(resourceRepo.findById(1L)).thenReturn(Optional.of(source));
        when(aclService.hasPermission(9L, 42L, AclResourceType.FOLDER, AclPermission.UPLOAD)).thenReturn(false);

        assertThatThrownBy(() -> service.enqueueClip(1L, 75_000L, 150_500L, null, 9L))
                .isInstanceOf(AccessDeniedException.class);

        verify(jobService, never()).createJob(any(), any(), any(), any());
    }

    @Test
    void enqueueClip_whenEndMsNotAfterStartMs_throwsIllegalArgumentWithoutTouchingRepoOrExecutor() {
        assertThatThrownBy(() -> service.enqueueClip(1L, 5_000L, 5_000L, null, 9L))
                .isInstanceOf(IllegalArgumentException.class);

        // Validierung muss VOR dem Laden der Resource greifen
        verify(resourceRepo, never()).findById(any());
        verify(jobService, never()).createJob(any(), any(), any(), any());
    }

    @Test
    void enqueueClip_whenTitleExceeds500Chars_throwsIllegalArgumentWithoutTouchingRepoOrExecutor() {
        String tooLongTitle = "x".repeat(501);

        assertThatThrownBy(() -> service.enqueueClip(1L, 75_000L, 150_500L, tooLongTitle, 9L))
                .isInstanceOf(IllegalArgumentException.class);

        // Validierung muss VOR dem Laden der Resource und VOR dem Job-Erstellen greifen —
        // ffmpeg soll nicht erst laufen, um dann an einem DB-Spaltenlimit zu scheitern.
        verify(resourceRepo, never()).findById(any());
        verify(jobService, never()).createJob(any(), any(), any(), any());
    }

    @Test
    void enqueueClip_whenSourceMimeTypeUnsupported_throwsIllegalArgumentWithoutCreatingJob() {
        Resource source = Resource.builder().id(1L).storedName("source-uuid")
                .originalName("Dokument.pdf").folderId(42L).uploaderId(7L)
                .mimeType("application/pdf").build();
        when(resourceRepo.findById(1L)).thenReturn(Optional.of(source));
        when(aclService.hasPermission(9L, 42L, AclResourceType.FOLDER, AclPermission.UPLOAD)).thenReturn(true);

        assertThatThrownBy(() -> service.enqueueClip(1L, 75_000L, 150_500L, null, 9L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(jobService, never()).createJob(any(), any(), any(), any());
    }

    @Test
    void enqueueClip_whenActiveDuplicateJobExists_returnsExistingJobIdWithoutCreatingNew() {
        Resource source = Resource.builder().id(1L).storedName("source-uuid")
                .originalName("Urlaub.mp4").folderId(42L).uploaderId(7L)
                .mimeType("video/mp4").build();
        when(resourceRepo.findById(1L)).thenReturn(Optional.of(source));
        when(aclService.hasPermission(9L, 42L, AclResourceType.FOLDER, AclPermission.UPLOAD)).thenReturn(true);

        ProcessingJob existing = ProcessingJob.builder().id(77L).resourceId(1L)
                .attempts(0).maxAttempts(3)
                .status(ProcessingJobStatus.PENDING)
                .result("{\"startMs\":75000,\"endMs\":150500}")
                .build();
        when(jobRepository.findByResourceIdAndTypeAndStatusIn(1L, ProcessingJobType.VIDEO_CLIP, ACTIVE_STATUSES))
                .thenReturn(List.of(existing));

        long jobId = service.enqueueClip(1L, 75_000L, 150_500L, null, 9L);

        assertThat(jobId).isEqualTo(77L);
        verify(jobService, never()).createJob(any(), any(), any(), any());
    }

    @Test
    void getActiveClipJobs_delegatesToRepositoryWithCorrectArgs() {
        ProcessingJob job = ProcessingJob.builder().id(3L).resourceId(1L).build();
        when(jobRepository.findByResourceIdAndTypeAndStatusIn(1L, ProcessingJobType.VIDEO_CLIP, ACTIVE_STATUSES))
                .thenReturn(List.of(job));

        List<ProcessingJob> result = service.getActiveClipJobs(1L);

        assertThat(result).containsExactly(job);
        verify(jobRepository).findByResourceIdAndTypeAndStatusIn(1L, ProcessingJobType.VIDEO_CLIP, ACTIVE_STATUSES);
    }
}
