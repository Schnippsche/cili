package de.toengi.cili.controller;

import de.toengi.cili.dto.search.FacetsResponse;
import de.toengi.cili.dto.search.SearchResponse;
import de.toengi.cili.service.SearchService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Value("${cili.search.max-page-size:200}")
    private int maxPageSize;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SearchResponse> search(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) Long folder,
            @RequestParam(required = false) String mimeType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int tPage) {

        int clampedSize = Math.clamp(size, 1, maxPageSize);

        Sort sorting;
        if ("date".equalsIgnoreCase(sort)) {
            sorting = Sort.by(Sort.Direction.DESC, "uploadedAt");
        } else if ("name".equalsIgnoreCase(sort)) {
            sorting = Sort.by(Sort.Direction.ASC, "originalName");
        } else {
            sorting = Sort.unsorted();
        }
        PageRequest pageable = PageRequest.of(page, clampedSize, sorting);

        return ResponseEntity.ok(searchService.search(q, folder, mimeType, pageable, tPage));
    }

    @GetMapping("/facets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FacetsResponse> getFacets(
            @RequestParam(required = false, defaultValue = "") String q) {
        return ResponseEntity.ok(searchService.getFacets(q));
    }
}
