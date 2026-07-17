package de.toengi.cili.service;

import de.toengi.cili.dto.collection.CollectionShareInfoDto;
import de.toengi.cili.dto.collection.CollectionShareTokenDto;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Collection;
import de.toengi.cili.model.entity.CollectionItem;
import de.toengi.cili.model.entity.CollectionShareToken;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.repository.*;
import de.toengi.cili.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionShareServiceTest {

    @Mock CollectionShareTokenRepository tokenRepo;
    @Mock CollectionRepository collectionRepo;
    @Mock CollectionItemRepository itemRepo;
    @Mock ResourceRepository resourceRepo;
    @Mock SubtitleTrackRepository subtitleTrackRepo;
    @Mock ThumbnailRepository thumbnailRepo;
    @Mock TestimonialService testimonialService;
    @Mock StreamService streamService;
    @Mock ThumbnailService thumbnailService;
    @Mock StorageService storageService;

    @InjectMocks CollectionShareService service;

    static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30);
    static final LocalDateTime PAST   = LocalDateTime.now().minusDays(1);

    Collection ownedCollection() {
        return Collection.builder().id(10L).userId(1L).name("Meine Sammlung").build();
    }

    @Test
    void createShare_whenOwned_createsNewToken() {
        when(collectionRepo.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(ownedCollection()));
        when(tokenRepo.findByCollectionId(10L)).thenReturn(Optional.empty());
        CollectionShareToken saved = CollectionShareToken.builder()
                .token("new-uuid").collectionId(10L).createdBy(1L).expiresAt(FUTURE).build();
        when(tokenRepo.save(any())).thenReturn(saved);

        CollectionShareTokenDto result = service.createShare(1L, 10L);

        assertThat(result.token()).isEqualTo("new-uuid");
        assertThat(result.collectionId()).isEqualTo(10L);
        verify(tokenRepo).save(any());
    }

    @Test
    void createShare_whenTokenExists_replacesIt() {
        CollectionShareToken existing = CollectionShareToken.builder()
                .token("old").collectionId(10L).expiresAt(FUTURE).build();
        when(collectionRepo.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(ownedCollection()));
        when(tokenRepo.findByCollectionId(10L)).thenReturn(Optional.of(existing));
        CollectionShareToken fresh = CollectionShareToken.builder()
                .token("new").collectionId(10L).createdBy(1L).expiresAt(FUTURE).build();
        when(tokenRepo.save(any())).thenReturn(fresh);

        CollectionShareTokenDto result = service.createShare(1L, 10L);

        verify(tokenRepo).delete(existing);
        assertThat(result.token()).isEqualTo("new");
    }

    @Test
    void createShare_whenNotOwned_throwsForbidden() {
        when(collectionRepo.findByIdAndUserId(10L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createShare(99L, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void getShare_whenValidToken_returnsPresent() {
        when(collectionRepo.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(ownedCollection()));
        CollectionShareToken token = CollectionShareToken.builder()
                .token("tok").collectionId(10L).expiresAt(FUTURE).build();
        when(tokenRepo.findByCollectionId(10L)).thenReturn(Optional.of(token));

        assertThat(service.getShare(1L, 10L)).isPresent();
    }

    @Test
    void getShare_whenExpired_returnsEmpty() {
        when(collectionRepo.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(ownedCollection()));
        CollectionShareToken token = CollectionShareToken.builder()
                .token("tok").collectionId(10L).expiresAt(PAST).build();
        when(tokenRepo.findByCollectionId(10L)).thenReturn(Optional.of(token));

        assertThat(service.getShare(1L, 10L)).isEmpty();
    }

    @Test
    void revokeShare_deletesToken() {
        when(collectionRepo.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(ownedCollection()));
        CollectionShareToken token = CollectionShareToken.builder().token("tok").collectionId(10L).expiresAt(FUTURE).build();
        when(tokenRepo.findByCollectionId(10L)).thenReturn(Optional.of(token));

        service.revokeShare(1L, 10L);

        verify(tokenRepo).delete(token);
    }

    @Test
    void revokeShare_whenNotOwned_throwsForbidden() {
        when(collectionRepo.findByIdAndUserId(10L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeShare(99L, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void getInfo_whenTokenExpired_throwsGone() {
        CollectionShareToken expired = CollectionShareToken.builder()
                .token("tok").collectionId(10L).expiresAt(PAST).build();
        when(tokenRepo.findByToken("tok")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.getInfo("tok"))
                .isInstanceOf(CiliException.class)
                .satisfies(e -> assertThat(((CiliException) e).getStatus()).isEqualTo(HttpStatus.GONE));
    }

    @Test
    void getInfo_whenTokenNotFound_throws404() {
        when(tokenRepo.findByToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInfo("bad"))
                .isInstanceOf(CiliException.class)
                .satisfies(e -> assertThat(((CiliException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getInfo_whenValid_returnsDto() {
        CollectionShareToken token = CollectionShareToken.builder()
                .token("tok").collectionId(10L).expiresAt(FUTURE).build();
        when(tokenRepo.findByToken("tok")).thenReturn(Optional.of(token));
        when(collectionRepo.findById(10L)).thenReturn(Optional.of(ownedCollection()));
        when(itemRepo.findByCollectionIdAndResourceIdIsNotNullOrderByAddedAtDesc(10L)).thenReturn(List.of());
        when(itemRepo.findByCollectionIdAndTestimonialIdIsNotNullOrderByAddedAtDesc(10L)).thenReturn(List.of());
        when(thumbnailRepo.findByResourceIdIn(List.of())).thenReturn(List.of());
        when(subtitleTrackRepo.findByResourceIdIn(List.of())).thenReturn(List.of());
        when(testimonialService.getPublicByIds(List.of())).thenReturn(List.of());

        CollectionShareInfoDto result = service.getInfo("tok");

        assertThat(result.collectionName()).isEqualTo("Meine Sammlung");
        assertThat(result.resources()).isEmpty();
        assertThat(result.testimonials()).isEmpty();
    }

    @Test
    void streamResource_whenNotMember_throwsForbidden() {
        CollectionShareToken token = CollectionShareToken.builder()
                .token("tok").collectionId(10L).expiresAt(FUTURE).build();
        when(tokenRepo.findByToken("tok")).thenReturn(Optional.of(token));
        when(itemRepo.existsByCollectionIdAndResourceId(10L, 99L)).thenReturn(false);
        Resource r = Resource.builder().id(99L).testimonialId(null).build();
        when(resourceRepo.findById(99L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.streamResource("tok", 99L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void getThumbnail_whenNotMember_throwsForbidden() throws Exception {
        CollectionShareToken token = CollectionShareToken.builder()
                .token("tok").collectionId(10L).expiresAt(FUTURE).build();
        when(tokenRepo.findByToken("tok")).thenReturn(Optional.of(token));
        when(itemRepo.existsByCollectionIdAndResourceId(10L, 99L)).thenReturn(false);
        Resource r = Resource.builder().id(99L).testimonialId(null).build();
        when(resourceRepo.findById(99L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.getThumbnail("tok", 99L, "small"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void getSubtitleText_whenNotMember_throwsForbidden() {
        CollectionShareToken token = CollectionShareToken.builder()
                .token("tok").collectionId(10L).expiresAt(FUTURE).build();
        when(tokenRepo.findByToken("tok")).thenReturn(Optional.of(token));
        when(itemRepo.existsByCollectionIdAndResourceId(10L, 99L)).thenReturn(false);
        Resource r = Resource.builder().id(99L).testimonialId(null).build();
        when(resourceRepo.findById(99L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.getSubtitleText("tok", 99L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void streamResource_whenIndirectMemberViaTestimonial_allowed() throws Exception {
        CollectionShareToken token = CollectionShareToken.builder()
                .token("tok").collectionId(10L).expiresAt(FUTURE).build();
        when(tokenRepo.findByToken("tok")).thenReturn(Optional.of(token));
        when(itemRepo.existsByCollectionIdAndResourceId(10L, 99L)).thenReturn(false);
        Resource r = Resource.builder().id(99L).testimonialId(42L).build();
        when(resourceRepo.findById(99L)).thenReturn(Optional.of(r));
        when(itemRepo.existsByCollectionIdAndTestimonialId(10L, 42L)).thenReturn(true);
        when(streamService.streamPublic(eq(99L), any())).thenReturn(null);

        service.streamResource("tok", 99L, null);

        verify(streamService).streamPublic(eq(99L), any());
    }
}
