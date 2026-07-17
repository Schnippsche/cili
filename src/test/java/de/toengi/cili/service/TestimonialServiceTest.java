package de.toengi.cili.service;

import de.toengi.cili.dto.testimonial.CreateTestimonialRequest;
import de.toengi.cili.dto.testimonial.UpdateTestimonialRequest;
import de.toengi.cili.dto.testimonial.TestimonialDto;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.Testimonial;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.UserRole;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.TestimonialRepository;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.storage.StorageService;
import java.io.IOException;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestimonialServiceTest {

    @Mock TestimonialRepository repository;
    @Mock ResourceRepository resourceRepository;
    @Mock StorageService storageService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AclService aclService;
    @Mock ThumbnailService thumbnailService;
    @InjectMocks TestimonialService service;

    private CiliUserDetails mockUser;

    @BeforeEach
    void setUpSecurity() {
        mockUser = mock(CiliUserDetails.class);
        when(mockUser.getUserId()).thenReturn(1L);
        when(mockUser.getRole()).thenReturn(UserRole.USER);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(mockUser);

        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        when(resourceRepository.findByTestimonialIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());
        when(aclService.hasTestimonialsPermission(anyLong(), any(AclPermission.class))).thenReturn(true);
    }

    private void mockAuthenticatedUser(Long userId, UserRole role) {
        when(mockUser.getUserId()).thenReturn(userId);
        when(mockUser.getRole()).thenReturn(role);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_savesWithCurrentUserId() {
        var req = new CreateTestimonialRequest("Max Mustermann", null, "Sehr gute Erfahrung, gerne wieder.", null, null);
        var saved = Testimonial.builder()
            .id(1L).authorName("Max Mustermann").text("Sehr gute Erfahrung, gerne wieder.")
            .userId(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(repository.save(any())).thenReturn(saved);

        var result = service.create(req, List.of());

        assertThat(result.authorName()).isEqualTo("Max Mustermann");
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.images()).isEmpty();
    }

    @Test
    void create_withSource_persistsSourceOnEntity() {
        var req = new CreateTestimonialRequest("Max Mustermann", null, "Sehr gute Erfahrung, gerne wieder.", null, "telegram-tiere");
        var captor = org.mockito.ArgumentCaptor.forClass(Testimonial.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> {
            Testimonial t = inv.getArgument(0);
            t.setId(1L);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            return t;
        });

        service.create(req, List.of());

        assertThat(captor.getValue().getSource()).isEqualTo("telegram-tiere");
    }

    @Test
    void create_withoutSource_leavesSourceNull() {
        var req = new CreateTestimonialRequest("Max Mustermann", null, "Sehr gute Erfahrung, gerne wieder.", null, null);
        var captor = org.mockito.ArgumentCaptor.forClass(Testimonial.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> {
            Testimonial t = inv.getArgument(0);
            t.setId(1L);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            return t;
        });

        service.create(req, List.of());

        assertThat(captor.getValue().getSource()).isNull();
    }

    @Test
    void delete_byOwner_callsRepositoryDelete() {
        var t = Testimonial.builder().id(1L).userId(1L).build();
        when(repository.findById(1L)).thenReturn(Optional.of(t));

        assertThatNoException().isThrownBy(() -> service.delete(1L));
        verify(repository).delete(t);
    }

    @Test
    void delete_byOtherUser_throwsForbidden() {
        var t = Testimonial.builder().id(1L).userId(99L).build();
        when(repository.findById(1L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(CiliException.class)
            .hasMessageContaining("Zugriff verweigert");
    }

    @Test
    void delete_byAdmin_succeeds() {
        when(mockUser.getRole()).thenReturn(UserRole.ADMIN);
        var t = Testimonial.builder().id(1L).userId(99L).build();
        when(repository.findById(1L)).thenReturn(Optional.of(t));

        assertThatNoException().isThrownBy(() -> service.delete(1L));
        verify(repository).delete(t);
    }

    @Test
    void update_byOwner_changesFields() {
        var req = new UpdateTestimonialRequest("Neuer Name", null, "Aktualisierter Text mit mehr Inhalt.");
        var t = Testimonial.builder().id(1L).userId(1L)
            .authorName("Alter Name").text("Alter Text").build();
        when(repository.findById(1L)).thenReturn(Optional.of(t));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.update(1L, req, List.of(), List.of());

        assertThat(result.authorName()).isEqualTo("Neuer Name");
    }

    @Test
    void delete_notFound_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_withQuery_callsSearchLike() {
        when(repository.searchLike(anyList(), any(Pageable.class))).thenReturn(Page.empty());

        service.list("testsuche", PageRequest.of(0, 10));

        verify(repository).searchLike(eq(List.of("testsuche")), any(Pageable.class));
        verify(repository, never()).findAllByOrderByCreatedAtDesc(any());
    }

    @Test
    void list_withoutReadPermission_throwsAccessDenied() {
        mockAuthenticatedUser(10L, UserRole.USER);
        when(aclService.hasTestimonialsPermission(10L, AclPermission.READ)).thenReturn(false);

        assertThatThrownBy(() -> service.list(null, PageRequest.of(0, 10)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void create_withoutWritePermission_throwsAccessDenied() {
        mockAuthenticatedUser(10L, UserRole.USER);
        when(aclService.hasTestimonialsPermission(10L, AclPermission.WRITE)).thenReturn(false);

        assertThatThrownBy(() -> service.create(
            new CreateTestimonialRequest("Name", null, "Text that is long enough", null, null), null))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listAll_returnsAllTestimonialsDescending() {
        Testimonial t1 = Testimonial.builder().id(1L).authorName("A").text("x").userId(1L)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        Testimonial t2 = Testimonial.builder().id(2L).authorName("B").text("y").userId(1L)
                .createdAt(LocalDateTime.now().minusDays(1)).updatedAt(LocalDateTime.now()).build();
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(t1, t2));
        when(resourceRepository.findByTestimonialIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());

        var result = service.listAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).authorName()).isEqualTo("A");
    }

    @Test
    void getPublicThumbnailBytes_returnsBytes_forTestimonialResource() throws IOException {
        Resource r = new Resource();
        r.setId(5L);
        r.setTestimonialId(10L);
        r.setMimeType("image/jpeg");
        when(resourceRepository.findById(5L)).thenReturn(Optional.of(r));
        when(thumbnailService.getThumbnailBytesNoAcl(5L, "small")).thenReturn(new byte[]{42});

        byte[] result = service.getPublicThumbnailBytes(5L, "small");

        assertThat(result).isEqualTo(new byte[]{42});
    }

    @Test
    void getPublicThumbnailBytes_throwsForbidden_forNonTestimonialResource() {
        Resource r = new Resource();
        r.setId(6L);
        r.setTestimonialId(null);
        when(resourceRepository.findById(6L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.getPublicThumbnailBytes(6L, "small"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403");
    }

    @Test
    void getPublicThumbnailBytes_throwsNotFound_forMissingResource() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicThumbnailBytes(99L, "small"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getByIds_returnsMappedDtos() {
        Testimonial t1 = Testimonial.builder().id(1L).authorName("A").text("Text A")
                .userId(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(repository.findAllById(List.of(1L))).thenReturn(List.of(t1));

        List<TestimonialDto> result = service.getByIds(List.of(1L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).authorName()).isEqualTo("A");
    }

    @Test
    void getByIds_emptyList_returnsEmptyWithoutQuerying() {
        List<TestimonialDto> result = service.getByIds(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(repository);
    }
}
