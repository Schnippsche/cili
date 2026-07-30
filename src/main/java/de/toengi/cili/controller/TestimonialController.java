package de.toengi.cili.controller;

import de.toengi.cili.dto.testimonial.*;
import de.toengi.cili.service.TestimonialService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/testimonials")
@RequiredArgsConstructor
public class TestimonialController {

    private final TestimonialService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<TestimonialDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.list(q, source, PageRequest.of(page, Math.min(size, 50)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public TestimonialDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public TestimonialDto create(
            @RequestParam String authorName,
            @RequestParam(required = false) String tags,
            @RequestParam String text,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) LocalDateTime createdAt,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return service.create(new CreateTestimonialRequest(authorName, tags, text, source, createdAt), images);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public TestimonialDto update(
            @PathVariable Long id,
            @RequestParam String authorName,
            @RequestParam(required = false) String tags,
            @RequestParam String text,
            @RequestParam(required = false) String source,
            @RequestPart(value = "images", required = false) List<MultipartFile> newImages,
            @RequestParam(value = "deleteAttachmentIds", required = false) List<Long> deleteAttachmentIds) {
        return service.update(id, new UpdateTestimonialRequest(authorName, tags, text, source), newImages, deleteAttachmentIds);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @DeleteMapping("/{id}/attachments/{resourceId}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable Long id, @PathVariable Long resourceId) {
        service.deleteAttachment(id, resourceId);
    }
}
