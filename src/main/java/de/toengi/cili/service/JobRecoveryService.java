package de.toengi.cili.service;

import de.toengi.cili.config.VideoWorkflowConfig;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobRecoveryService {

    // Diese Job-Typen steuern externe Python-Prozesse und können nicht neu gestartet werden.
    // Bei Startup → als FAILED markieren; bei Zombie-Timeout → ebenfalls FAILED (nicht re-dispatchen).
    private static final Set<ProcessingJobType> EXTERNAL_PROCESS_TYPES = EnumSet.of(
        ProcessingJobType.TELEGRAM_IMPORT,
        ProcessingJobType.VIDEO_URL_IMPORT,
        ProcessingJobType.VIDEO_CLIP
    );

    private final ProcessingJobRepository repo;
    private final JobDispatcher dispatcher;
    private final ProcessingJobService jobService;
    private final VideoWorkflowConfig config;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverOnStartup() {
        List<ProcessingJob> all = repo.findByStatusIn(
            List.of(ProcessingJobStatus.RUNNING, ProcessingJobStatus.PENDING));

        List<ProcessingJob> external = all.stream()
            .filter(j -> EXTERNAL_PROCESS_TYPES.contains(j.getType()))
            .toList();
        List<ProcessingJob> normal = all.stream()
            .filter(j -> !EXTERNAL_PROCESS_TYPES.contains(j.getType()))
            .toList();

        if (!external.isEmpty()) {
            log.warn("Found {} stuck external-process jobs at startup — marking FAILED", external.size());
            external.forEach(j -> jobService.markFailed(j, "JVM neu gestartet — externer Prozess abgebrochen"));
        }

        if (!normal.isEmpty()) {
            log.warn("Found {} RUNNING/PENDING jobs at startup — resetting to PENDING and re-dispatching", normal.size());
            List<Long> ids = normal.stream().map(ProcessingJob::getId).toList();
            repo.resetJobsToPending(ids);
            for (ProcessingJob job : normal) {
                dispatcher.dispatch(job);
            }
        }

        if (external.isEmpty() && normal.isEmpty()) {
            log.info("No stuck jobs found at startup.");
        }
    }

    @Scheduled(fixedDelayString = "PT5M")
    @Transactional
    public void recoverZombieJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(config.getZombieTimeoutMinutes());
        List<ProcessingJob> zombies = repo.findZombieJobs(cutoff);
        if (zombies.isEmpty()) return;

        List<ProcessingJob> external = zombies.stream()
            .filter(j -> EXTERNAL_PROCESS_TYPES.contains(j.getType()))
            .toList();
        List<ProcessingJob> normal = zombies.stream()
            .filter(j -> !EXTERNAL_PROCESS_TYPES.contains(j.getType()))
            .toList();

        if (!external.isEmpty()) {
            log.warn("Found {} zombie external-process jobs — marking FAILED", external.size());
            external.forEach(j -> jobService.markFailed(j, "Timeout — externer Prozess nicht mehr erreichbar"));
        }

        if (!normal.isEmpty()) {
            log.warn("Found {} zombie jobs — resetting to PENDING", normal.size());
            List<Long> ids = normal.stream().map(ProcessingJob::getId).toList();
            repo.resetJobsToPending(ids);
            for (ProcessingJob job : normal) {
                dispatcher.dispatch(job);
            }
        }
    }
}
