package de.toengi.cili.controller.admin;

import de.toengi.cili.dto.gpu.GpuMetricSampleDto;
import de.toengi.cili.repository.GpuMetricSampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/gpu")
@RequiredArgsConstructor
public class AdminGpuController {

    private static final Set<Integer> ALLOWED_HOURS = Set.of(1, 6, 24, 168);

    private final GpuMetricSampleRepository repository;

    @GetMapping("/current")
    public ResponseEntity<GpuMetricSampleDto> current() {
        return repository.findTopByOrderBySampledAtDesc()
            .map(GpuMetricSampleDto::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/history")
    public List<GpuMetricSampleDto> history(@RequestParam(defaultValue = "24") int hours) {
        if (!ALLOWED_HOURS.contains(hours)) {
            throw new IllegalArgumentException("hours muss einer von " + ALLOWED_HOURS + " sein");
        }
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return repository.findBySampledAtAfterOrderBySampledAtAsc(since).stream()
            .map(GpuMetricSampleDto::from)
            .toList();
    }
}
