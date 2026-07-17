package de.toengi.cili.service;

import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingJobService {

    private final ProcessingJobRepository repo;

    @Autowired @Lazy
    private ProcessingJobService self;

    @Transactional
    public ProcessingJob createJob(Long resourceId, ProcessingJobType type,
                                   Long parentJobId, String initialResult) {
        ProcessingJob job = ProcessingJob.builder()
            .resourceId(resourceId)
            .type(type)
            .status(ProcessingJobStatus.PENDING)
            .parentJobId(parentJobId)
            .result(initialResult)
            .build();
        return repo.save(job);
    }

    @Transactional
    public ProcessingJob createSystemJob(ProcessingJobType type) {
        return createSystemJob(type, null);
    }

    @Transactional
    public ProcessingJob createSystemJob(ProcessingJobType type, String initialResult) {
        return createSystemJob(type, null, initialResult);
    }

    @Transactional
    public ProcessingJob createSystemJob(ProcessingJobType type, String source, String initialResult) {
        ProcessingJob job = ProcessingJob.builder()
            .type(type)
            .source(source)
            .status(ProcessingJobStatus.PENDING)
            .maxAttempts(1)
            .result(initialResult)
            .build();
        return repo.save(job);
    }

    @Transactional
    public Optional<ProcessingJob> claimNextJob(ProcessingJobType type) {
        Optional<Long> jobId = repo.findNextClaimableJobId(type.name());
        if (jobId.isEmpty()) return Optional.empty();
        ProcessingJob job = repo.findById(jobId.get()).orElseThrow();
        self.markRunning(job, buildWorkerLock());
        return Optional.of(job);
    }

    @Transactional
    public void markRunning(ProcessingJob job, String workerLock) {
        job.setStatus(ProcessingJobStatus.RUNNING);
        job.setWorkerLock(workerLock);
        job.setStartedAt(LocalDateTime.now());
        job.setAttempts(job.getAttempts() + 1);
        repo.save(job);
    }

    @Transactional
    public void markDone(ProcessingJob job, String resultJson) {
        job.setStatus(ProcessingJobStatus.DONE);
        job.setWorkerLock(null);
        job.setFinishedAt(LocalDateTime.now());
        job.setResult(resultJson);
        repo.save(job);
    }

    @Transactional
    public void markFailed(ProcessingJob job, String errorMessage) {
        job.setWorkerLock(null);
        job.setErrorMessage(errorMessage);
        if (job.getAttempts() < job.getMaxAttempts()) {
            job.setStatus(ProcessingJobStatus.PENDING);
            log.warn("Job {} failed (attempt {}/{}), will retry: {}",
                job.getId(), job.getAttempts(), job.getMaxAttempts(), errorMessage);
        } else {
            job.setStatus(ProcessingJobStatus.FAILED);
            job.setFinishedAt(LocalDateTime.now());
            log.error("Job {} permanently failed after {} attempts: {}",
                job.getId(), job.getAttempts(), errorMessage);
        }
        repo.save(job);
    }

    public ProcessingJob reloadJob(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalStateException("Job not found: " + id));
    }

    @Transactional
    public void updateResult(Long jobId, String resultJson) {
        ProcessingJob job = repo.findById(jobId).orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
        job.setResult(resultJson);
        repo.save(job);
    }

    @Transactional
    public void cancelJob(ProcessingJob job, String reason) {
        job.setStatus(ProcessingJobStatus.CANCELLED);
        job.setWorkerLock(null);
        job.setFinishedAt(LocalDateTime.now());
        job.setErrorMessage("Cancelled: " + reason);
        repo.save(job);
        log.info("Job {} ({}) cancelled: {}", job.getId(), job.getType(), reason);
    }

    @Transactional
    public void cancelWhisperJobIfActive(Long resourceId) {
        repo.findByResourceIdAndType(resourceId, ProcessingJobType.WHISPER_TRANSCRIBE)
            .filter(j -> j.getStatus() == ProcessingJobStatus.PENDING
                      || j.getStatus() == ProcessingJobStatus.RUNNING)
            .ifPresent(j -> self.cancelJob(j, "Manual VTT uploaded"));
    }

    public boolean hasActiveTranscriptionJob(Long resourceId) {
        return repo.existsActiveTranscriptionJob(resourceId);
    }

    private String buildWorkerLock() {
        String host = "unknown";
        try { host = InetAddress.getLocalHost().getHostName(); } catch (Exception ignored) {}
        return host + ":" + Thread.currentThread().getName() + ":" + UUID.randomUUID();
    }
}
