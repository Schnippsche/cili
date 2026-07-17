package de.toengi.cili.service;

import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.TelegramImportConfig;
import de.toengi.cili.util.PythonProcessUtils;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramImportService {

    private final TelegramImportConfig config;
    private final CiliGlobalConfig global;
    private final ProcessingJobService jobService;
    private final ProcessingJobRepository jobRepository;

    @Autowired @Lazy
    private TelegramImportService self;

    public boolean isRunning() {
        return jobRepository.existsByTypeAndStatusIn(
            ProcessingJobType.TELEGRAM_IMPORT,
            List.of(ProcessingJobStatus.PENDING, ProcessingJobStatus.RUNNING));
    }

    /**
     * Erstellt einen Job-Eintrag und startet das Python-Skript asynchron.
     * Wirft IllegalStateException wenn bereits ein Import läuft.
     */
    public ProcessingJob triggerAndRun() {
        if (isRunning()) {
            throw new IllegalStateException("Ein Telegram-Import-Job läuft bereits");
        }
        ProcessingJob job = jobService.createSystemJob(ProcessingJobType.TELEGRAM_IMPORT);
        self.executeAsync(job.getId());
        return job;
    }

    @Async("telegramExecutor")
    public void executeAsync(Long jobId) {
        ProcessingJob job = jobService.reloadJob(jobId);
        jobService.markRunning(job, "telegram-import");
        log.info("Telegram-Import gestartet (Job {})", jobId);

        List<String> cmd = buildCommand();
        ProcessBuilder pb = PythonProcessUtils.forScript(cmd, global.resolve(config.getScriptName()));
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> log.info("[telegram-import] {}", line));
            }

            boolean finished = process.waitFor(config.getTimeoutMinutes(), TimeUnit.MINUTES);
            job = jobService.reloadJob(jobId);

            if (!finished) {
                process.destroyForcibly();
                jobService.markFailed(job, "Timeout nach " + config.getTimeoutMinutes() + " Minuten");
                log.warn("Telegram-Import abgebrochen: Timeout (Job {})", jobId);
            } else if (process.exitValue() == 0) {
                jobService.markDone(job, null);
                log.info("Telegram-Import erfolgreich abgeschlossen (Job {})", jobId);
            } else {
                jobService.markFailed(job, "Skript beendet mit Exit-Code " + process.exitValue());
                log.error("Telegram-Import fehlgeschlagen: Exit-Code {} (Job {})", process.exitValue(), jobId);
            }
        } catch (Exception e) {
            log.error("Telegram-Import: unerwarteter Fehler (Job {})", jobId, e);
            try {
                job = jobService.reloadJob(jobId);
                jobService.markFailed(job, e.getMessage());
            } catch (Exception ignored) {}
        }
    }

    private List<String> buildCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add(global.getPythonPath());
        cmd.add(global.resolve(config.getScriptName()));
        String envPath = global.resolve(config.getEnvName());
        if (envPath != null) {
            cmd.add("--env");
            cmd.add(envPath);
        }
        return cmd;
    }
}
