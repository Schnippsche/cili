package de.toengi.cili.controller;

import de.toengi.cili.dto.testimonial.PublicTestimonialDto;
import de.toengi.cili.service.TestimonialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicTestimonialControllerTest {

    @Mock TestimonialService testimonialService;
    @InjectMocks PublicTestimonialController controller;

    @Test
    void listAll_delegatesToService() {
        PublicTestimonialDto dto = new PublicTestimonialDto(
            1L, "Anna", null, "Super Erfahrung",
            LocalDateTime.now(), LocalDateTime.now(), List.of());
        when(testimonialService.listAllPublic(any(Pageable.class))).thenReturn(List.of(dto));

        List<PublicTestimonialDto> result = controller.listAll(0, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).authorName()).isEqualTo("Anna");
        verify(testimonialService).listAllPublic(PageRequest.of(0, 50));
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
}
