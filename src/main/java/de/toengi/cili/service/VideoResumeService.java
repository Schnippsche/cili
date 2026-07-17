package de.toengi.cili.service;

import de.toengi.cili.dto.media.VideoResumeDto;
import de.toengi.cili.model.entity.VideoResume;
import de.toengi.cili.model.entity.VideoResumeId;
import de.toengi.cili.repository.VideoResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoResumeService {

    private final VideoResumeRepository videoResumeRepository;

    @Transactional(readOnly = true)
    public VideoResumeDto getResume(Long resourceId, Long userId) {
        VideoResumeId key = new VideoResumeId(userId, resourceId);
        VideoResume resume = videoResumeRepository.findById(key)
                .orElse(new VideoResume(key, 0, null));
        return new VideoResumeDto(resourceId, resume.getPositionSeconds(), resume.getUpdatedAt());
    }

    @Transactional
    public VideoResumeDto saveResume(Long resourceId, int positionSeconds, Long userId) {
        VideoResumeId key = new VideoResumeId(userId, resourceId);
        try {
            VideoResume resume = videoResumeRepository.findById(key)
                    .orElse(new VideoResume(key, 0, null));
            resume.setPositionSeconds(positionSeconds);
            resume = videoResumeRepository.saveAndFlush(resume);
            return new VideoResumeDto(resourceId, resume.getPositionSeconds(), resume.getUpdatedAt());
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert race — reload and update
            VideoResume resume = videoResumeRepository.findById(key).orElseThrow();
            resume.setPositionSeconds(positionSeconds);
            resume = videoResumeRepository.save(resume);
            return new VideoResumeDto(resourceId, resume.getPositionSeconds(), resume.getUpdatedAt());
        }
    }
}
