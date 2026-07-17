package de.toengi.cili.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ai_summaries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"resource_id", "language_code"}))
@Getter @Setter @NoArgsConstructor
public class AiSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(name = "summary", columnDefinition = "MEDIUMTEXT")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
