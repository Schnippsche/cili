package de.toengi.cili.repository;

import de.toengi.cili.model.entity.VideoResume;
import de.toengi.cili.model.entity.VideoResumeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoResumeRepository extends JpaRepository<VideoResume, VideoResumeId> {
}
