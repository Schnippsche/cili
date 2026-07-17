package de.toengi.cili.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_resumes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class VideoResume {

    @EmbeddedId
    private VideoResumeId id;

    @Column(name = "position_seconds", nullable = false)
    private int positionSeconds;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
