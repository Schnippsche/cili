package de.toengi.cili.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mailflow_instances")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MailflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "flow_name", nullable = false, length = 100)
    private String flowName;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;
}
