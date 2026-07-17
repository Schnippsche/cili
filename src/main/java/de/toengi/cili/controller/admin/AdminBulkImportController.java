package de.toengi.cili.controller.admin;

import de.toengi.cili.dto.bulkimport.*;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.BulkImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/bulk-imports")
@RequiredArgsConstructor
public class AdminBulkImportController {

    private final BulkImportService bulkImportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateBulkImportResponse create(@Valid @RequestBody CreateBulkImportRequest request,
                                           @AuthenticationPrincipal CiliUserDetails currentUser) {
        return bulkImportService.createImport(request, currentUser.getUserId());
    }

    @GetMapping("/{jobId}")
    public BulkImportJobDto getJob(@PathVariable String jobId,
                                   @AuthenticationPrincipal CiliUserDetails currentUser) {
        return bulkImportService.getJob(jobId, currentUser.getUserId());
    }

    @PostMapping("/{jobId}/items/{itemId}/fail")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void failItem(@PathVariable String jobId, @PathVariable Long itemId,
                         @Valid @RequestBody FailBulkImportItemRequest request,
                         @AuthenticationPrincipal CiliUserDetails currentUser) {
        bulkImportService.failItem(jobId, itemId, request.errorMessage(), currentUser.getUserId());
    }
}
