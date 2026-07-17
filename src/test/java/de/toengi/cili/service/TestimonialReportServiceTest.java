package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.config.ReportConfig;
import de.toengi.cili.dto.report.ReportImageDto;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.Testimonial;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.TestimonialRepository;
import de.toengi.cili.repository.ThumbnailRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.thymeleaf.TemplateEngine;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestimonialReportServiceTest {

    @Mock TestimonialRepository testimonialRepo;
    @Mock ResourceRepository resourceRepo;
    @Mock ThumbnailRepository thumbnailRepo;
    @Mock StorageService storageService;
    @Mock TemplateEngine templateEngine;
    @Mock ReportConfig reportConfig;
    @Mock FileStorageConfig fileStorageConfig;
    @Mock CommandRunner commandRunner;
    @InjectMocks TestimonialReportService service;

    @BeforeEach
    void setup() {
        when(reportConfig.getMaxResults()).thenReturn(50);
    }

    private Testimonial testimonial(long id) {
        Testimonial t = new Testimonial();
        t.setId(id);
        t.setAuthorName("Autor " + id);
        t.setText("Text " + id);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    @Test
    void fetchAll_blank_query_calls_findTopN() {
        when(testimonialRepo.findTopNByCreatedAtDesc(50))
            .thenReturn(List.of(testimonial(1L)));
        List<Testimonial> result = service.fetchAll("", 50);
        assertThat(result).hasSize(1);
        verify(testimonialRepo).findTopNByCreatedAtDesc(50);
    }

    @Test
    void fetchAll_with_query_calls_searchLikeTop() {
        when(testimonialRepo.searchLikeTop(List.of("pflege"), 50))
            .thenReturn(List.of(testimonial(1L)));
        List<Testimonial> result = service.fetchAll("pflege", 50);
        assertThat(result).hasSize(1);
        verify(testimonialRepo).searchLikeTop(List.of("pflege"), 50);
    }

    @Test
    void loadImagesAsBase64_returns_base64_for_small_image() throws IOException {
        Testimonial t = testimonial(1L);
        Resource r = new Resource();
        r.setId(10L);
        r.setStoredName("img.jpg");
        r.setMimeType("image/jpeg");
        r.setOriginalName("photo.jpg");
        r.setSize(100L);

        when(resourceRepo.findByTestimonialIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(r));
        when(thumbnailRepo.findByResourceId(10L)).thenReturn(java.util.Optional.empty());
        when(storageService.retrieve("img.jpg"))
            .thenReturn(new ByteArrayInputStream("JPEG".getBytes()));

        Map<Long, List<ReportImageDto>> result = service.loadImagesAsBase64(List.of(t));
        assertThat(result).containsKey(1L);
        assertThat(result.get(1L)).hasSize(1);
        assertThat(result.get(1L).get(0).mimeType()).isEqualTo("image/jpeg");
        assertThat(result.get(1L).get(0).base64Data()).isNotBlank();
    }

    @Test
    void renderHtml_passes_correct_context_to_template_engine() {
        Testimonial t = testimonial(1L);
        when(resourceRepo.findByTestimonialIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(templateEngine.process(eq("testimonial-report"), any(org.thymeleaf.context.Context.class)))
            .thenReturn("<html>rendered</html>");

        String result = service.renderHtml("pflege", List.of(t), false, 50);

        assertThat(result).isEqualTo("<html>rendered</html>");

        ArgumentCaptor<org.thymeleaf.context.Context> ctxCaptor =
            ArgumentCaptor.forClass(org.thymeleaf.context.Context.class);
        verify(templateEngine).process(eq("testimonial-report"), ctxCaptor.capture());
        org.thymeleaf.context.Context ctx = ctxCaptor.getValue();
        assertThat(ctx.getVariable("query")).isEqualTo("pflege");
        assertThat(ctx.getVariable("totalCount")).isEqualTo(1);
        assertThat(ctx.getVariable("truncated")).isEqualTo(false);
        assertThat(ctx.getVariable("maxResults")).isEqualTo(50);
    }

    @Test
    void fetchByIds_reorders_results_to_match_input_id_order() {
        // findAllById liefert absichtlich in einer anderen Reihenfolge als angefragt
        when(testimonialRepo.findAllById(List.of(3L, 1L, 2L)))
            .thenReturn(List.of(testimonial(1L), testimonial(2L), testimonial(3L)));

        List<Testimonial> result = service.fetchByIds(List.of(3L, 1L, 2L), 50);

        assertThat(result).extracting(Testimonial::getId).containsExactly(3L, 1L, 2L);
    }

    @Test
    void fetchByIds_skips_missing_id_silently() {
        // ID 2L existiert nicht (mehr) -> findAllById liefert nur 1L und 3L zurück
        when(testimonialRepo.findAllById(List.of(1L, 2L, 3L)))
            .thenReturn(List.of(testimonial(1L), testimonial(3L)));

        List<Testimonial> result = service.fetchByIds(List.of(1L, 2L, 3L), 50);

        assertThat(result).extracting(Testimonial::getId).containsExactly(1L, 3L);
    }

    @Test
    void fetchByIds_truncates_to_max_after_reordering() {
        when(testimonialRepo.findAllById(List.of(1L, 2L, 3L)))
            .thenReturn(List.of(testimonial(1L), testimonial(2L), testimonial(3L)));

        List<Testimonial> result = service.fetchByIds(List.of(1L, 2L, 3L), 2);

        assertThat(result).extracting(Testimonial::getId).containsExactly(1L, 2L);
    }

    @Test
    void loadImagesAsBase64_skips_image_on_io_error() throws IOException {
        Testimonial t = testimonial(1L);
        Resource r = new Resource();
        r.setId(10L); r.setStoredName("bad.jpg"); r.setMimeType("image/jpeg");
        r.setOriginalName("bad.jpg"); r.setSize(100L);

        when(resourceRepo.findByTestimonialIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(r));
        when(thumbnailRepo.findByResourceId(10L)).thenReturn(java.util.Optional.empty());
        when(storageService.retrieve("bad.jpg")).thenThrow(new IOException("not found"));

        Map<Long, List<ReportImageDto>> result = service.loadImagesAsBase64(List.of(t));
        assertThat(result.getOrDefault(1L, List.of())).isEmpty();
    }
}
