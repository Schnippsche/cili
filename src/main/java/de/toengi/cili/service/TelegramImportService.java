package de.toengi.cili.service;

import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.TelegramImportConfig;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.security.JwtTokenProvider;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramImportService {

    /** Deutlich über cili.telegram.timeout-minutes hinaus gültig — Downloads großer Webinar-Videos
     *  können 15+ Minuten dauern, ein knapp bemessenes Token liefe mitten im Lauf ab (401). */
    private static final long JOB_TOKEN_EXPIRY_MS = 4 * 60 * 60 * 1000L; // 4 Stunden

    private final TelegramImportConfig config;
    private final CiliGlobalConfig global;
    private final ProcessingJobService jobService;
    private final ProcessingJobRepository jobRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Autowired @Lazy
    private TelegramImportService self;

    public boolean isRunning(String source) {
        return jobRepository.existsByTypeAndSourceAndStatusIn(
            ProcessingJobType.TELEGRAM_IMPORT, source,
            List.of(ProcessingJobStatus.PENDING, ProcessingJobStatus.RUNNING));
    }

    public List<TelegramImportConfig.Source> listSources() {
        return config.getSources();
    }

    /**
     * Erstellt einen Job-Eintrag für die angegebene Quelle und startet das
     * Python-Skript asynchron. Wirft IllegalArgumentException bei unbekannter
     * Quelle, IllegalStateException wenn für diese Quelle bereits ein Import läuft.
     */
    public ProcessingJob triggerAndRun(String source) {
        TelegramImportConfig.Source cfg = config.findSource(source)
            .orElseThrow(() -> new IllegalArgumentException("Unbekannte Telegram-Quelle: " + source));
        if (isRunning(source)) {
            throw new IllegalStateException("Ein Telegram-Import-Job für '" + source + "' läuft bereits");
        }
        ProcessingJob job = jobService.createSystemJob(ProcessingJobType.TELEGRAM_IMPORT, source, null);
        self.executeAsync(job.getId(), cfg);
        return job;
    }

    @Async("telegramExecutor")
    public void executeAsync(Long jobId, TelegramImportConfig.Source source) {
        ProcessingJob job = jobService.reloadJob(jobId);
        jobService.markRunning(job, "telegram-import");
        log.info("Telegram-Import gestartet (Job {}, Quelle {})", jobId, source.getName());

        List<String> cmd = buildCommand(source);
        ProcessBuilder pb = PythonProcessUtils.forScript(cmd, global.resolve(source.getScriptName()));
        pb.redirectErrorStream(true);

        try {
            CiliUserDetails jobUser = (CiliUserDetails) userDetailsService.loadUserByUsername(config.getCiliUser());
            String jobToken = jwtTokenProvider.generateJobToken(jobUser, JOB_TOKEN_EXPIRY_MS);
            pb.environment().put("CILI_TOKEN", jobToken);
        } catch (Exception e) {
            log.warn("Telegram-Import ({}): konnte kein Job-Token für Benutzer '{}' generieren, "
                + "Skript fällt auf CILI_USER/CILI_PASS-Login aus der .env-Datei zurück: {}",
                source.getName(), config.getCiliUser(), e.getMessage());
        }

        try {
            Process process = pb.start();

            // Heartbeat: JobRecoveryService killt RUNNING-Jobs, deren updatedAt seit
            // cili.video-workflow.zombie-timeout-minutes nicht mehr aktualisiert wurde. Ohne
            // periodisches Touch sähe ein aktiv laufender, aber >45-Min-dauernder Import immer
            // wie ein hängengebliebener Prozess aus. Throttled auf max. alle 2 Minuten, damit
            // nicht jede einzelne (teils sehr häufige) Ausgabezeile einen DB-Write auslöst.
            AtomicLong lastHeartbeat = new AtomicLong(0);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> {
                    log.info("[telegram-import:{}] {}", source.getName(), line);
                    long now = System.nanoTime();
                    if (now - lastHeartbeat.get() > TimeUnit.MINUTES.toNanos(2)) {
                        lastHeartbeat.set(now);
                        jobService.touch(jobId);
                    }
                });
            }

            boolean finished = process.waitFor(config.getTimeoutMinutes(), TimeUnit.MINUTES);
            job = jobService.reloadJob(jobId);

            if (!finished) {
                process.destroyForcibly();
                jobService.markFailed(job, "Timeout nach " + config.getTimeoutMinutes() + " Minuten");
                log.warn("Telegram-Import ({}) abgebrochen: Timeout (Job {})", source.getName(), jobId);
            } else if (process.exitValue() == 0) {
                jobService.markDone(job, null);
                log.info("Telegram-Import ({}) erfolgreich abgeschlossen (Job {})", source.getName(), jobId);
            } else {
                jobService.markFailed(job, "Skript beendet mit Exit-Code " + process.exitValue());
                log.error("Telegram-Import ({}) fehlgeschlagen: Exit-Code {} (Job {})",
                    source.getName(), process.exitValue(), jobId);
            }
        } catch (Exception e) {
            log.error("Telegram-Import ({}): unerwarteter Fehler (Job {})", source.getName(), jobId, e);
            try {
                job = jobService.reloadJob(jobId);
                jobService.markFailed(job, e.getMessage());
            } catch (Exception ignored) {}
        }
    }

    private List<String> buildCommand(TelegramImportConfig.Source source) {
        List<String> cmd = new ArrayList<>();
        cmd.add(global.getPythonPath());
        cmd.add(global.resolve(source.getScriptName()));
        String envPath = global.resolve(source.getEnvName());
        if (envPath != null) {
            cmd.add("--env");
            cmd.add(envPath);
        }
        return cmd;
    }
}
