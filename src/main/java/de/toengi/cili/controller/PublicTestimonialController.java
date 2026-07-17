package de.toengi.cili.controller;

import de.toengi.cili.dto.testimonial.PublicTestimonialDto;
import de.toengi.cili.service.TestimonialService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/public/testimonials")
@RequiredArgsConstructor
public class PublicTestimonialController {

    private final TestimonialService testimonialService;

    private static final int MAX_PAGE_SIZE = 100;

    @GetMapping
    public List<PublicTestimonialDto> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        return testimonialService.listAllPublic(PageRequest.of(page, cappedSize));
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
}
