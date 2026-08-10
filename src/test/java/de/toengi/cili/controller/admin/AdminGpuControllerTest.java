package de.toengi.cili.controller.admin;

import de.toengi.cili.model.entity.GpuMetricSample;
import de.toengi.cili.repository.GpuMetricSampleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminGpuControllerTest {

    @Mock GpuMetricSampleRepository repository;
    @InjectMocks AdminGpuController controller;

    @Test
    void current_noSamples_returns204() {
        when(repository.findTopByOrderBySampledAtDesc()).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.current();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void current_sampleExists_returns200WithDto() {
        GpuMetricSample sample = GpuMetricSample.builder().id(1L).temperatureC(60).sampledAt(LocalDateTime.now()).build();
        when(repository.findTopByOrderBySampledAtDesc()).thenReturn(Optional.of(sample));

        ResponseEntity<?> response = controller.current();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void history_validHours_returnsMappedList() {
        GpuMetricSample sample = GpuMetricSample.builder().id(1L).temperatureC(60).sampledAt(LocalDateTime.now()).build();
        when(repository.findBySampledAtAfterOrderBySampledAtAsc(any())).thenReturn(List.of(sample));

        var result = controller.history(24);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    void history_invalidHours_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> controller.history(2))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
