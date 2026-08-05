// src/test/java/de/toengi/cili/model/entity/MailflowStepStatusTest.java
package de.toengi.cili.model.entity;

import de.toengi.cili.model.enums.MailflowStepState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MailflowStepStatusTest {

    @Test
    void builder_withoutExplicitStatus_defaultsToPendingWithZeroAttempts() {
        MailflowStepStatus step = MailflowStepStatus.builder()
                .instanceId(1L)
                .stepId("welcome")
                .scheduledFor(LocalDate.of(2026, 8, 5))
                .build();

        assertThat(step.getStatus()).isEqualTo(MailflowStepState.PENDING);
        assertThat(step.getAttemptCount()).isZero();
        assertThat(step.getSentAt()).isNull();
        assertThat(step.getLastError()).isNull();
    }
}
