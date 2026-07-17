package de.toengi.cili.controller.admin;

import de.toengi.cili.dto.common.PageResponse;
import de.toengi.cili.dto.job.ProcessingJobDto;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.service.TelegramImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/jobs")
@RequiredArgsConstructor
public class AdminJobController {

    private final ProcessingJobRepository repo;
    private final TelegramImportService telegramImportService;

    @GetMapping
    public PageResponse<ProcessingJobDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) ProcessingJobStatus status) {

        var pageable = PageRequest.of(page, Math.min(size, 200));
        Page<ProcessingJobDto> jobs = (status != null
                ? repo.findByStatusOrderByCreatedAtDesc(status, pageable)
                : repo.findAllByOrderByCreatedAtDesc(pageable))
                .map(ProcessingJobDto::from);
        return new PageResponse<>(jobs.getContent(), page, jobs.getSize(),
                jobs.getTotalElements(), jobs.getTotalPages());
    }

    @GetMapping("/{id}")
    public ProcessingJobDto getOne(@PathVariable Long id) {
        return repo.findById(id).map(ProcessingJobDto::from)
                .orElseThrow(() -> new de.toengi.cili.exception.ResourceNotFoundException("Job", id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOne(@PathVariable Long id) {
        repo.deleteById(id);
    }

    @DeleteMapping("/completed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteCompleted() {
        repo.deleteByStatusIn(List.of(ProcessingJobStatus.DONE, ProcessingJobStatus.CANCELLED));
    }

    @PostMapping("/telegram-import/trigger")
    public ResponseEntity<?> triggerTelegramImport() {
        try {
            ProcessingJobDto job = ProcessingJobDto.from(telegramImportService.triggerAndRun());
            return ResponseEntity.ok(job);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage()));
        }
    }
}
