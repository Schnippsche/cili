package de.toengi.cili.controller;

import de.toengi.cili.dto.media.SubtitleTrackDto;
import de.toengi.cili.dto.testimonial.PublicTestimonialDto;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.service.StreamService;
import de.toengi.cili.service.SubtitleService;
import de.toengi.cili.service.TestimonialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/public/testimonials")
@RequiredArgsConstructor
public class PublicTestimonialController {

    private final TestimonialService testimonialService;
    private final StreamService streamService;
    private final SubtitleService subtitleService;

    private static final int MAX_PAGE_SIZE = 100;

    @GetMapping
    public Page<PublicTestimonialDto> listAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int cappedSize = Math.min(size, MAX_PAGE_SIZE);

        if (q == null || q.isBlank()) {
            log.info("Public testimonial page visited: source={}", source);
        } else {
            log.info("Public testimonial search performed: query='{}', source={}", q, source);
        }

        return testimonialService.listAllPublic(q, source, PageRequest.of(page, cappedSize));
    }

    @GetMapping("/{id}")
    public PublicTestimonialDto getOne(@PathVariable Long id) {
        log.info("Public testimonial detail viewed: id={}", id);
        return testimonialService.getPublicById(id);
    }

    @GetMapping("/images/{resourceId}")
    public ResponseEntity<byte[]> getImage(
            @PathVariable Long resourceId,
            @RequestParam(defaultValue = "small") String size) throws IOException {
        byte[] bytes = testimonialService.getPublicThumbnailBytes(resourceId, size);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "max-age=604800")
                .contentType(MediaType.IMAGE_JPEG)
                .body(bytes);
    }

    @GetMapping("/{id}/stream/{resourceId}")
    public ResponseEntity<ResourceRegion> stream(
            @PathVariable Long id,
            @PathVariable Long resourceId,
            @RequestHeader HttpHeaders headers) throws IOException {
        testimonialService.assertPublicAttachment(id, resourceId);
        return streamService.streamPublic(resourceId, headers);
    }

    @GetMapping("/{id}/subtitles/{resourceId}")
    public List<SubtitleTrackDto> subtitles(
            @PathVariable Long id,
            @PathVariable Long resourceId) {
        testimonialService.assertPublicAttachment(id, resourceId);
        return subtitleService.listNoAcl(resourceId);
    }

    @GetMapping("/{id}/subtitles/{resourceId}/{trackId}")
    public ResponseEntity<StreamingResponseBody> subtitleTrack(
            @PathVariable Long id,
            @PathVariable Long resourceId,
            @PathVariable Long trackId) throws IOException {
        testimonialService.assertPublicAttachment(id, resourceId);
        SubtitleService.SubtitleDownload result = subtitleService.downloadNoAcl(resourceId, trackId);
        StreamingResponseBody body = out -> {
            try (InputStream stream = result.stream()) {
                stream.transferTo(out);
            }
        };
        String contentType = result.format() == SubtitleFormat.VTT ? "text/vtt" : "text/plain";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(body);
    }
}
