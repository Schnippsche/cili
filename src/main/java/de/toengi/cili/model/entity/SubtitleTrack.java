package de.toengi.cili.model.entity;

import de.toengi.cili.model.enums.SubtitleFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "subtitle_tracks")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SubtitleTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(length = 100)
    private String label;

    @Column(name = "stored_name", nullable = false, length = 36)
    private String storedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubtitleFormat format;

    @Column(name = "text_content", columnDefinition = "MEDIUMTEXT")
    private String textContent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
