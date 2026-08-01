package de.toengi.cili.controller;

import de.toengi.cili.dto.media.SubtitleTrackDto;
import de.toengi.cili.dto.testimonial.PublicTestimonialDto;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.service.SubtitleService;
import de.toengi.cili.service.TestimonialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicTestimonialControllerTest {

    @Mock TestimonialService testimonialService;
    @Mock SubtitleService subtitleService;
    @InjectMocks PublicTestimonialController controller;

    @Test
    void listAll_delegatesToService() {
        PublicTestimonialDto dto = new PublicTestimonialDto(
            1L, "Anna", null, "Super Erfahrung", true, false,
            LocalDateTime.now(), LocalDateTime.now(), List.of());
        Pageable pageable = PageRequest.of(0, 50);
        when(testimonialService.listAllPublic(isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(dto), pageable, 1));

        Page<PublicTestimonialDto> result = controller.listAll(null, null, 0, 50);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).authorName()).isEqualTo("Anna");
        verify(testimonialService).listAllPublic(null, null, pageable);
    }

    @Test
    void listAll_passesQueryAndSourceThrough() {
        Pageable pageable = PageRequest.of(0, 50);
        when(testimonialService.listAllPublic(eq("hund"), eq("Tier"), any(Pageable.class)))
            .thenReturn(Page.empty());

        controller.listAll("hund", "Tier", 0, 50);

        verify(testimonialService).listAllPublic("hund", "Tier", pageable);
    }

    @Test
    void getOne_delegatesToService() {
        PublicTestimonialDto dto = new PublicTestimonialDto(
            1L, "Anna", null, "Super Erfahrung", true, false,
            LocalDateTime.now(), LocalDateTime.now(), List.of());
        when(testimonialService.getPublicById(1L)).thenReturn(dto);

        PublicTestimonialDto result = controller.getOne(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.authorName()).isEqualTo("Anna");
        verify(testimonialService).getPublicById(1L);
    }

    @Test
    void getOne_propagatesNotFoundFromService() {
        when(testimonialService.getPublicById(99L))
            .thenThrow(new de.toengi.cili.exception.ResourceNotFoundException("Testimonial", 99L));

        assertThatThrownBy(() -> controller.getOne(99L))
            .isInstanceOf(de.toengi.cili.exception.ResourceNotFoundException.class);
    }

    @Test
    void getImage_returnsOkWithCacheControl() throws IOException {
        when(testimonialService.getPublicThumbnailBytes(1L, "small"))
            .thenReturn(new byte[]{1, 2, 3});

        ResponseEntity<byte[]> response = controller.getImage(1L, "small");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(new byte[]{1, 2, 3});
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=604800");
    }

    @Test
    void subtitles_assertsPublicAttachmentThenDelegatesToSubtitleService() {
        SubtitleTrackDto track = new SubtitleTrackDto(5L, 900L, "de", "Deutsch", SubtitleFormat.VTT, LocalDateTime.now(), true);
        when(subtitleService.listNoAcl(900L)).thenReturn(List.of(track));

        List<SubtitleTrackDto> result = controller.subtitles(1L, 900L);

        assertThat(result).containsExactly(track);
        verify(testimonialService).assertPublicAttachment(1L, 900L);
        verify(subtitleService).listNoAcl(900L);
    }

    @Test
    void subtitleTrack_assertsPublicAttachmentAndStreamsVttContent() throws IOException {
        byte[] vttBytes = "WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nHallo".getBytes();
        when(subtitleService.downloadNoAcl(900L, 5L))
            .thenReturn(new SubtitleService.SubtitleDownload(new ByteArrayInputStream(vttBytes), SubtitleFormat.VTT));

        ResponseEntity<StreamingResponseBody> response = controller.subtitleTrack(1L, 900L, 5L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).hasToString("text/vtt");
        verify(testimonialService).assertPublicAttachment(1L, 900L);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out.toByteArray()).isEqualTo(vttBytes);
    }
}
