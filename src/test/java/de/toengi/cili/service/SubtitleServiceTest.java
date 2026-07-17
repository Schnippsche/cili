package de.toengi.cili.service;

import de.toengi.cili.dto.media.SubtitleTrackDto;
import de.toengi.cili.exception.ConflictException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.enums.StorageType;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import de.toengi.cili.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubtitleServiceTest {

    @Mock SubtitleTrackRepository subtitleTrackRepository;
    @Mock ResourceRepository resourceRepository;
    @Mock StorageService storageService;
    @Mock ProcessingJobService jobService;

    @InjectMocks SubtitleService subtitleService;

    // --- list ---

    @Test
    void list_returnsAllTracksForResource() {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        when(subtitleTrackRepository.findByResourceId(1L)).thenReturn(
                List.of(track(10L, 1L, "en"), track(11L, 1L, "de")));

        List<SubtitleTrackDto> result = subtitleService.list(1L, 99L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).languageCode()).isEqualTo("en");
        assertThat(result.get(1).languageCode()).isEqualTo("de");
    }

    @Test
    void list_resourceNotFound_throws() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> subtitleService.list(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- upload ---

    @Test
    void upload_storesFileAndCreatesTrack() throws IOException {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        when(subtitleTrackRepository.existsByResourceIdAndLanguageCode(1L, "en")).thenReturn(false);
        when(storageService.store(any(), anyLong())).thenReturn("stored-uuid");
        when(subtitleTrackRepository.save(any())).thenAnswer(inv -> {
            SubtitleTrack t = inv.getArgument(0);
            return SubtitleTrack.builder().id(10L).resourceId(t.getResourceId())
                    .languageCode(t.getLanguageCode()).label(t.getLabel())
                    .storedName(t.getStoredName()).format(t.getFormat())
                    .textContent(t.getTextContent())
                    .createdAt(LocalDateTime.now()).build();
        });

        byte[] bytes = "WEBVTT\n\n".getBytes();
        SubtitleTrackDto result = subtitleService.upload(1L, "en", "English", SubtitleFormat.VTT, bytes, 99L);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.languageCode()).isEqualTo("en");
        assertThat(result.format()).isEqualTo(SubtitleFormat.VTT);
        verify(storageService).store(any(), eq((long) bytes.length));
    }

    @Test
    void upload_storesTextContent() throws IOException {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        when(subtitleTrackRepository.existsByResourceIdAndLanguageCode(1L, "de")).thenReturn(false);
        when(storageService.store(any(), anyLong())).thenReturn("stored-uuid");
        ArgumentCaptor<SubtitleTrack> captor = ArgumentCaptor.forClass(SubtitleTrack.class);
        when(subtitleTrackRepository.save(captor.capture())).thenAnswer(inv -> {
            SubtitleTrack t = inv.getArgument(0);
            return SubtitleTrack.builder().id(5L).resourceId(t.getResourceId())
                    .languageCode(t.getLanguageCode()).storedName("x")
                    .format(t.getFormat()).textContent(t.getTextContent())
                    .createdAt(LocalDateTime.now()).build();
        });

        byte[] bytes = "1\n00:00:01,000 --> 00:00:02,000\nHallo Welt\n".getBytes();
        subtitleService.upload(1L, "de", null, SubtitleFormat.SRT, bytes, 99L);

        assertThat(captor.getValue().getTextContent()).contains("Hallo Welt");
    }

    @Test
    void upload_nonUtf8Bytes_storesEmptyString() throws IOException {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        when(subtitleTrackRepository.existsByResourceIdAndLanguageCode(1L, "de")).thenReturn(false);
        when(storageService.store(any(), anyLong())).thenReturn("stored-uuid");
        ArgumentCaptor<SubtitleTrack> captor = ArgumentCaptor.forClass(SubtitleTrack.class);
        when(subtitleTrackRepository.save(captor.capture())).thenAnswer(inv -> {
            SubtitleTrack t = inv.getArgument(0);
            return SubtitleTrack.builder().id(5L).resourceId(1L)
                    .languageCode("de").storedName("x")
                    .format(SubtitleFormat.SRT).textContent(t.getTextContent())
                    .createdAt(LocalDateTime.now()).build();
        });

        // Invalid UTF-8 sequence
        byte[] invalidBytes = new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0x00};
        subtitleService.upload(1L, "de", null, SubtitleFormat.SRT, invalidBytes, 99L);

        // spec requires empty string (not null) so FULLTEXT index still works
        assertThat(captor.getValue().getTextContent()).isEmpty();
    }

    @Test
    void upload_duplicateLanguage_throws() {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        when(subtitleTrackRepository.existsByResourceIdAndLanguageCode(1L, "en")).thenReturn(true);

        assertThatThrownBy(() -> subtitleService.upload(1L, "en", null, SubtitleFormat.SRT, new byte[0], 1L))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(storageService);
    }

    @Test
    void upload_resourceNotFound_throws() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> subtitleService.upload(99L, "en", null, SubtitleFormat.SRT, new byte[0], 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void upload_saveFails_deletesOrphanedFile() throws IOException {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        when(subtitleTrackRepository.existsByResourceIdAndLanguageCode(1L, "en")).thenReturn(false);
        when(storageService.store(any(), anyLong())).thenReturn("stored-uuid");
        when(subtitleTrackRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> subtitleService.upload(1L, "en", null, SubtitleFormat.SRT, new byte[0], 99L))
                .isInstanceOf(RuntimeException.class);

        verify(storageService).delete("stored-uuid");
    }

    @Test
    void upload_textExceeds500k_isTruncated() throws IOException {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        when(subtitleTrackRepository.existsByResourceIdAndLanguageCode(1L, "de")).thenReturn(false);
        when(storageService.store(any(), anyLong())).thenReturn("stored-uuid");
        ArgumentCaptor<SubtitleTrack> captor = ArgumentCaptor.forClass(SubtitleTrack.class);
        when(subtitleTrackRepository.save(captor.capture())).thenAnswer(inv -> {
            SubtitleTrack t = inv.getArgument(0);
            return SubtitleTrack.builder().id(5L).resourceId(1L)
                    .languageCode("de").storedName("x")
                    .format(SubtitleFormat.SRT).textContent(t.getTextContent())
                    .createdAt(LocalDateTime.now()).build();
        });

        byte[] bigBytes = "A".repeat(600_000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        subtitleService.upload(1L, "de", null, SubtitleFormat.SRT, bigBytes, 99L);

        assertThat(captor.getValue().getTextContent()).hasSize(500_000);
    }

    // --- getText ---

    @Test
    void getText_returnsStoredTextContent() {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        SubtitleTrack t = trackWithContent(10L, 1L, "de", "Hallo Welt");
        when(subtitleTrackRepository.findByIdAndResourceId(10L, 1L)).thenReturn(Optional.of(t));

        String result = subtitleService.getText(1L, 10L, 99L);

        assertThat(result).isEqualTo("Hallo Welt");
    }

    @Test
    void getText_nullContent_returnsEmptyString() {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        SubtitleTrack t = track(10L, 1L, "de"); // textContent is null
        when(subtitleTrackRepository.findByIdAndResourceId(10L, 1L)).thenReturn(Optional.of(t));

        String result = subtitleService.getText(1L, 10L, 99L);

        assertThat(result).isEmpty();
    }

    @Test
    void getText_trackNotFound_throws() {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        when(subtitleTrackRepository.findByIdAndResourceId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subtitleService.getText(1L, 99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- download ---

    @Test
    void download_returnsInputStream() throws IOException {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        SubtitleTrack t = track(10L, 1L, "de");
        when(subtitleTrackRepository.findByIdAndResourceId(10L, 1L)).thenReturn(Optional.of(t));
        when(storageService.retrieve(t.getStoredName()))
                .thenReturn(new ByteArrayInputStream("content".getBytes()));

        SubtitleService.SubtitleDownload result = subtitleService.download(1L, 10L, 99L);

        assertThat(result).isNotNull();
    }

    // --- delete ---

    @Test
    void delete_removesStorageAndEntity() throws IOException {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        SubtitleTrack t = track(10L, 1L, "fr");
        when(subtitleTrackRepository.findByIdAndResourceId(10L, 1L)).thenReturn(Optional.of(t));

        subtitleService.delete(1L, 10L, 99L);

        verify(storageService).delete(t.getStoredName());
        verify(subtitleTrackRepository).delete(t);
    }

    @Test
    void delete_trackNotFound_throws() {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L)));
        when(subtitleTrackRepository.findByIdAndResourceId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subtitleService.delete(1L, 99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- helpers ---

    private Resource resource(Long id) {
        return Resource.builder().id(id).folderId(10L).originalName("video.mp4")
                .storedName("abc").mimeType("video/mp4").size(100L).uploaderId(1L)
                .storageType(StorageType.LOCAL)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private SubtitleTrack track(Long id, Long resourceId, String lang) {
        return SubtitleTrack.builder().id(id).resourceId(resourceId)
                .languageCode(lang).label(lang.toUpperCase())
                .storedName("sub-" + id).format(SubtitleFormat.VTT)
                .createdAt(LocalDateTime.now()).build();
    }

    private SubtitleTrack trackWithContent(Long id, Long resourceId, String lang, String content) {
        return SubtitleTrack.builder().id(id).resourceId(resourceId)
                .languageCode(lang).label(lang.toUpperCase())
                .storedName("sub-" + id).format(SubtitleFormat.VTT)
                .textContent(content)
                .createdAt(LocalDateTime.now()).build();
    }
}
