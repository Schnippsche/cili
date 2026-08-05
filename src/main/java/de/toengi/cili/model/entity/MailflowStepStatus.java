package de.toengi.cili.model.entity;

import de.toengi.cili.model.enums.MailflowStepState;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "mailflow_step_status",
        uniqueConstraints = @UniqueConstraint(columnNames = {"instance_id", "step_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MailflowStepStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "step_id", nullable = false, length = 100)
    private String stepId;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDate scheduledFor;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MailflowStepState status = MailflowStepState.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
