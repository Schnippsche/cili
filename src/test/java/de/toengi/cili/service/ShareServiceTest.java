package de.toengi.cili.service;

import de.toengi.cili.dto.share.ShareInfoDto;
import de.toengi.cili.dto.share.ShareTokenDto;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.ShareToken;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.ShareTokenRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import de.toengi.cili.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock ShareTokenRepository shareTokenRepository;
    @Mock ResourceRepository resourceRepository;
    @Mock SubtitleTrackRepository subtitleTrackRepository;
    @Mock StreamService streamService;
    @Mock StorageService storageService;
    @InjectMocks ShareService shareService;

    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30);
    private static final LocalDateTime PAST   = LocalDateTime.now().minusDays(1);

    @Test
    void createShare_whenNoToken_createsNewToken() {
        when(shareTokenRepository.findByResourceId(1L)).thenReturn(Optional.empty());
        ShareToken saved = ShareToken.builder()
                .token("abc-uuid").resourceId(1L).createdBy(99L).expiresAt(FUTURE).build();
        when(shareTokenRepository.save(any())).thenReturn(saved);

        ShareTokenDto result = shareService.createShare(1L, 99L);

        assertThat(result.token()).isEqualTo("abc-uuid");
        assertThat(result.resourceId()).isEqualTo(1L);
        assertThat(result.expiresAt()).isAfter(LocalDateTime.now());
        verify(shareTokenRepository).save(any());
    }

    @Test
    void createShare_whenValidTokenExists_returnsExisting() {
        ShareToken existing = ShareToken.builder()
                .token("existing").resourceId(1L).createdBy(99L).expiresAt(FUTURE).build();
        when(shareTokenRepository.findByResourceId(1L)).thenReturn(Optional.of(existing));

        ShareTokenDto result = shareService.createShare(1L, 99L);

        assertThat(result.token()).isEqualTo("existing");
        verify(shareTokenRepository, never()).save(any());
    }

    @Test
    void createShare_whenTokenExpired_deletesAndCreatesNew() {
        ShareToken expired = ShareToken.builder()
                .token("old").resourceId(1L).createdBy(99L).expiresAt(PAST).build();
        ShareToken fresh = ShareToken.builder()
                .token("new").resourceId(1L).createdBy(99L).expiresAt(FUTURE).build();
        when(shareTokenRepository.findByResourceId(1L)).thenReturn(Optional.of(expired));
        when(shareTokenRepository.save(any())).thenReturn(fresh);

        ShareTokenDto result = shareService.createShare(1L, 99L);

        assertThat(result.token()).isEqualTo("new");
        verify(shareTokenRepository).delete(expired);
        verify(shareTokenRepository).save(any());
    }

    @Test
    void getShare_whenValidTokenExists_returnsPresent() {
        ShareToken token = ShareToken.builder().token("tok").resourceId(1L).expiresAt(FUTURE).build();
        when(shareTokenRepository.findByResourceId(1L)).thenReturn(Optional.of(token));

        Optional<ShareTokenDto> result = shareService.getShare(1L, 99L);

        assertThat(result).isPresent();
        assertThat(result.get().token()).isEqualTo("tok");
    }

    @Test
    void getShare_whenTokenExpired_returnsEmpty() {
        ShareToken token = ShareToken.builder().token("tok").resourceId(1L).expiresAt(PAST).build();
        when(shareTokenRepository.findByResourceId(1L)).thenReturn(Optional.of(token));

        assertThat(shareService.getShare(1L, 99L)).isEmpty();
    }

    @Test
    void getShare_whenNoToken_returnsEmpty() {
        when(shareTokenRepository.findByResourceId(1L)).thenReturn(Optional.empty());

        assertThat(shareService.getShare(1L, 99L)).isEmpty();
    }

    @Test
    void revokeShare_deletesToken() {
        ShareToken token = ShareToken.builder().token("tok").resourceId(1L).expiresAt(FUTURE).build();
        when(shareTokenRepository.findByResourceId(1L)).thenReturn(Optional.of(token));

        shareService.revokeShare(1L, 99L);

        verify(shareTokenRepository).delete(token);
    }

    @Test
    void revokeShare_whenNoToken_doesNothing() {
        when(shareTokenRepository.findByResourceId(1L)).thenReturn(Optional.empty());

        shareService.revokeShare(1L, 99L);

        verify(shareTokenRepository, never()).delete(any());
    }

    @Test
    void getShareInfo_returnsNameAndMimeType() {
        ShareToken token = ShareToken.builder().token("tok").resourceId(5L).expiresAt(FUTURE).build();
        Resource resource = Resource.builder()
                .id(5L).originalName("video.mp4").mimeType("video/mp4")
                .folderId(1L).uploaderId(1L).storedName("stored").build();
        when(shareTokenRepository.findByToken("tok")).thenReturn(Optional.of(token));
        when(resourceRepository.findById(5L)).thenReturn(Optional.of(resource));
        when(subtitleTrackRepository.findByResourceId(5L)).thenReturn(java.util.List.of());

        ShareInfoDto info = shareService.getShareInfo("tok");

        assertThat(info.originalName()).isEqualTo("video.mp4");
        assertThat(info.mimeType()).isEqualTo("video/mp4");
        assertThat(info.subtitles()).isEmpty();
    }

    @Test
    void getShareInfo_whenTokenExpired_throwsGone() {
        ShareToken token = ShareToken.builder().token("tok").resourceId(5L).expiresAt(PAST).build();
        when(shareTokenRepository.findByToken("tok")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> shareService.getShareInfo("tok"))
                .isInstanceOf(CiliException.class)
                .satisfies(e -> assertThat(((CiliException) e).getStatus()).isEqualTo(HttpStatus.GONE));
    }

    @Test
    void getShareInfo_whenTokenNotFound_throws() {
        when(shareTokenRepository.findByToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareService.getShareInfo("bad"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
