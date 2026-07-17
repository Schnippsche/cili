package de.toengi.cili.controller;

import de.toengi.cili.config.ReportConfig;
import de.toengi.cili.dto.collection.AddTestimonialToCollectionRequest;
import de.toengi.cili.dto.collection.AddToCollectionRequest;
import de.toengi.cili.dto.collection.CollectionDto;
import de.toengi.cili.dto.collection.CollectionNameRequest;
import de.toengi.cili.dto.collection.CreateCollectionRequest;
import de.toengi.cili.dto.collection.CreateFromTemplateRequest;
import de.toengi.cili.dto.resource.ResourceDto;
import de.toengi.cili.dto.testimonial.TestimonialDto;
import de.toengi.cili.model.entity.Testimonial;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.CollectionService;
import de.toengi.cili.service.TestimonialReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;
    private final TestimonialReportService reportService;
    private final ReportConfig reportConfig;

    @GetMapping("/api/collections")
    public List<CollectionDto> list(@AuthenticationPrincipal CiliUserDetails user) {
        return collectionService.listForUser(user.getUserId());
    }

    @GetMapping("/api/collections/templates")
    public List<CollectionDto> listTemplates() {
        return collectionService.listTemplates();
    }

    @PostMapping("/api/collections")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionDto create(@RequestBody CreateCollectionRequest req,
                                @AuthenticationPrincipal CiliUserDetails user) {
        return collectionService.create(user.getUserId(), user.getRole(), req.name(), req.isTemplate());
    }

    @PostMapping("/api/collections/from-template")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionDto createFromTemplate(@RequestBody CreateFromTemplateRequest req,
                                            @AuthenticationPrincipal CiliUserDetails user) {
        return collectionService.createFromTemplate(user.getUserId(), req.templateId(), req.name());
    }

    @GetMapping("/api/collections/{id}")
    public CollectionDto getOne(@PathVariable Long id,
                                @AuthenticationPrincipal CiliUserDetails user) {
        return collectionService.getOne(user.getUserId(), user.getRole(), id);
    }

    @PatchMapping("/api/collections/{id}")
    public CollectionDto rename(@PathVariable Long id,
                                @RequestBody CollectionNameRequest req,
                                @AuthenticationPrincipal CiliUserDetails user) {
        return collectionService.rename(user.getUserId(), user.getRole(), id, req.name());
    }

    @DeleteMapping("/api/collections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal CiliUserDetails user) {
        collectionService.delete(user.getUserId(), user.getRole(), id);
    }

    @PostMapping("/api/collections/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionDto copy(@PathVariable Long id,
                              @RequestBody CollectionNameRequest req,
                              @AuthenticationPrincipal CiliUserDetails user) {
        return collectionService.copy(user.getUserId(), user.getRole(), id, req.name());
    }

    @GetMapping("/api/collections/{id}/items")
    public List<ResourceDto> listItems(@PathVariable Long id,
                                       @AuthenticationPrincipal CiliUserDetails user) {
        return collectionService.listItems(user.getUserId(), user.getRole(), id);
    }

    @PostMapping("/api/collections/{id}/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addItem(@PathVariable Long id,
                        @RequestBody AddToCollectionRequest req,
                        @AuthenticationPrincipal CiliUserDetails user) {
        collectionService.addItem(user.getUserId(), user.getRole(), id, req.resourceId());
    }

    @DeleteMapping("/api/collections/{id}/items/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@PathVariable Long id,
                           @PathVariable Long resourceId,
                           @AuthenticationPrincipal CiliUserDetails user) {
        collectionService.removeItem(user.getUserId(), user.getRole(), id, resourceId);
    }

    @GetMapping("/api/collections/{id}/testimonials")
    public List<TestimonialDto> listTestimonials(@PathVariable Long id,
                                                  @AuthenticationPrincipal CiliUserDetails user) {
        return collectionService.listTestimonialItems(user.getUserId(), user.getRole(), id);
    }

    @PostMapping("/api/collections/{id}/testimonials")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addTestimonial(@PathVariable Long id,
                               @RequestBody AddTestimonialToCollectionRequest req,
                               @AuthenticationPrincipal CiliUserDetails user) {
        collectionService.addTestimonialItem(user.getUserId(), user.getRole(), id, req.testimonialId());
    }

    @DeleteMapping("/api/collections/{id}/testimonials/{testimonialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTestimonial(@PathVariable Long id,
                                  @PathVariable Long testimonialId,
                                  @AuthenticationPrincipal CiliUserDetails user) {
        collectionService.removeTestimonialItem(user.getUserId(), user.getRole(), id, testimonialId);
    }

    @GetMapping("/api/collections/{id}/report/preview")
    public ResponseEntity<String> reportPreview(@PathVariable Long id,
                                                 @AuthenticationPrincipal CiliUserDetails user) {
        CollectionDto collection = collectionService.getOne(user.getUserId(), user.getRole(), id);
        List<Long> testimonialIds = collectionService.requireTestimonialIdsForReport(user.getUserId(), user.getRole(), id);
        int max = reportConfig.getMaxResults();
        List<Testimonial> hits = reportService.fetchByIds(testimonialIds, max);
        boolean truncated = testimonialIds.size() > max;
        String html = reportService.renderHtml(collection.name(), hits, truncated, max);
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
            .body(html);
    }
}
