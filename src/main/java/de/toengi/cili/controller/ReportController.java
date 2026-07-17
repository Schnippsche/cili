package de.toengi.cili.controller;

import de.toengi.cili.config.ReportConfig;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.AclService;
import de.toengi.cili.service.TestimonialReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/testimonials/report")
@RequiredArgsConstructor
public class ReportController {

    private final TestimonialReportService reportService;
    private final ReportConfig reportConfig;
    private final AclService aclService;

    @GetMapping("/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> preview(
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal CiliUserDetails userDetails) {
        if (userDetails == null || !aclService.hasTestimonialsPermission(userDetails.getUserId(), AclPermission.READ)) {
            throw new AccessDeniedException("Kein Zugriff auf Erfahrungsberichte");
        }
        int max = reportConfig.getMaxResults();
        var hits = reportService.fetchAll(q, max);
        boolean truncated = hits.size() >= max;
        String html = reportService.renderHtml(q, hits, truncated, max);
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
            .body(html);
    }
}
