package de.toengi.cili.controller;

import de.toengi.cili.config.ReportConfig;
import de.toengi.cili.dto.collection.CollectionDto;
import de.toengi.cili.model.entity.Testimonial;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.CollectionService;
import de.toengi.cili.service.TestimonialReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionControllerTest {

    @Mock private CollectionService collectionService;
    @Mock private TestimonialReportService reportService;
    @Mock private ReportConfig reportConfig;

    @InjectMocks private CollectionController controller;

    private final Long userId = 1L;
    private final Long otherUserId = 2L;

    private CiliUserDetails userDetails(Long id) {
        CiliUserDetails user = mock(CiliUserDetails.class);
        when(user.getUserId()).thenReturn(id);
        when(user.getRole()).thenReturn(de.toengi.cili.model.enums.UserRole.USER);
        return user;
    }

    @Test
    void reportPreview_owner_returnsRenderedHtmlUsingCollectionNameAsQuery() {
        CiliUserDetails user = userDetails(userId);
        CollectionDto collection = new CollectionDto(10L, "Meine Sammlung", 0L, 2L, false, LocalDateTime.now());
        Testimonial t = Testimonial.builder().id(1L).authorName("A").text("Text").userId(userId).build();

        when(collectionService.getOne(userId, user.getRole(), 10L)).thenReturn(collection);
        when(collectionService.requireTestimonialIdsForReport(userId, user.getRole(), 10L))
                .thenReturn(List.of(1L, 2L));
        when(reportConfig.getMaxResults()).thenReturn(50);
        when(reportService.fetchByIds(List.of(1L, 2L), 50)).thenReturn(List.of(t));
        when(reportService.renderHtml(eq("Meine Sammlung"), eq(List.of(t)), eq(false), eq(50)))
                .thenReturn("<html>report</html>");

        ResponseEntity<String> response = controller.reportPreview(10L, user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("<html>report</html>");
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("text/html");

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(reportService).renderHtml(queryCaptor.capture(), any(), eq(false), eq(50));
        assertThat(queryCaptor.getValue()).isEqualTo("Meine Sammlung");
    }

    @Test
    void reportPreview_foreignCollection_deniedBeforeGeneratingReport() {
        CiliUserDetails user = userDetails(otherUserId);
        when(collectionService.getOne(otherUserId, user.getRole(), 10L))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "Collection not found or access denied"));

        assertThatThrownBy(() -> controller.reportPreview(10L, user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        verifyNoInteractions(reportService);
        verify(collectionService, never()).requireTestimonialIdsForReport(any(), any(), any());
    }
}
