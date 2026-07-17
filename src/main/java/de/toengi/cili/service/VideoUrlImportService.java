package de.toengi.cili.service;

import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.VideoImportConfig;
import de.toengi.cili.util.PythonProcessUtils;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.enums.ProcessingJobType;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoUrlImportService {

    private final VideoImportConfig config;
    private final CiliGlobalConfig global;
    private final ProcessingJobService jobService;
    private final CookiesExpiryService cookiesExpiryService;

    @Autowired @Lazy
    private VideoUrlImportService self;

    /**
     * Legt einen Job an, trägt ihn in die Prozessliste ein und startet den Download asynchron.
     *
     * @param folderId Ziel-Ordner in CILI
     * @param url      Video-URL (Loom, YouTube, …)
     * @param password Optionales Video-Passwort (null wenn nicht benötigt)
     */
    public ProcessingJob submit(long folderId, String url, String password, String jwtToken) {
        String info = String.format(
            "{\"folderId\":%d,\"url\":\"%s\"}", folderId, url.replace("\"", "\\\""));
        ProcessingJob job = jobService.createSystemJob(ProcessingJobType.VIDEO_URL_IMPORT, info);
        self.executeAsync(job.getId(), folderId, url, password, jwtToken);
        return job;
    }

    @Async("videoImportExecutor")
    public void executeAsync(Long jobId, long folderId, String url, String password, String jwtToken) {
        ProcessingJob job = jobService.reloadJob(jobId);
        jobService.markRunning(job, "video-import");
        log.info("Video-URL-Import gestartet: {} → Ordner {} (Job {})", url, folderId, jobId);
        cookiesExpiryService.logStatus();

        List<String> cmd = buildCommand(folderId, url, password);
        ProcessBuilder pb = PythonProcessUtils.forScript(cmd, global.resolve(config.getScriptName()));
        pb.redirectErrorStream(true);
        pb.environment().put("CILI_URL", config.getCiliUrl());
        String cookiesFile = config.getCookiesFile();
        if (cookiesFile != null && !cookiesFile.isBlank()) {
            pb.environment().put("YTDLP_COOKIES_FILE", cookiesFile);
        }
        if (jwtToken != null) {
            pb.environment().put("CILI_TOKEN", jwtToken);
        } else {
            pb.environment().put("CILI_USER", config.getCiliUser());
            pb.environment().put("CILI_PASS", config.getCiliPass());
        }

        try {
            Process process = pb.start();
            AtomicInteger lastPct = new AtomicInteger(-1);
            AtomicReference<String> phase = new AtomicReference<>("download");
            AtomicReference<String> cookieError = new AtomicReference<>(null);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> {
                    if (line.contains("[download]") && line.contains("% of")) {
                        int pctIdx = line.indexOf('%');
                        if (pctIdx > 0) {
                            try {
                                String before = line.substring(0, pctIdx).trim();
                                String[] parts = before.split("\\s+");
                                double pct = Double.parseDouble(parts[parts.length - 1]);
                                int bucket = (int) (pct / 10) * 10;
                                if (bucket > lastPct.get()) {
                                    lastPct.set(bucket);
                                    log.info("[video-import] {}", line);
                                    if (bucket >= 100) {
                                        phase.set("processing");
                                        jobService.updateResult(jobId, "{\"phase\":\"processing\"}");
                                    } else {
                                        jobService.updateResult(jobId, "{\"phase\":\"download\",\"progress\":" + bucket + "}");
                                    }
                                }
                                return;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    if (line.contains("Upload:") && "processing".equals(phase.get())) {
                        phase.set("uploading");
                        jobService.updateResult(jobId, "{\"phase\":\"uploading\"}");
                    }
                    if (cookieError.get() == null && isCookieExpiredLine(line)) {
                        cookieError.set(line.strip());
                        log.warn("[video-import] Cookie-Problem erkannt: {}", line.strip());
                    }
                    log.info("[video-import] {}", line);
                });
            }
            boolean finished = process.waitFor(config.getTimeoutMinutes(), TimeUnit.MINUTES);
            job = jobService.reloadJob(jobId);
            if (!finished) {
                process.destroyForcibly();
                jobService.markFailed(job, "Timeout nach " + config.getTimeoutMinutes() + " Minuten");
            } else if (process.exitValue() == 0) {
                jobService.markDone(job, null);
                log.info("Video-URL-Import erfolgreich (Job {})", jobId);
            } else {
                String errMsg = cookieError.get() != null
                    ? "YouTube-Cookies abgelaufen oder ungültig – bitte erneuern. (" + cookieError.get() + ")"
                    : "Skript beendet mit Exit-Code " + process.exitValue();
                jobService.markFailed(job, errMsg);
            }
        } catch (Exception e) {
            log.error("Video-URL-Import fehlgeschlagen (Job {})", jobId, e);
            try { jobService.markFailed(jobService.reloadJob(jobId), e.getMessage()); }
            catch (Exception ignored) {}
        }
    }

    private static boolean isCookieExpiredLine(String line) {
        String l = line.toLowerCase();
        return l.contains("sign in to confirm")
            || l.contains("please sign in")
            || l.contains("cookie")  && (l.contains("expired") || l.contains("invalid") || l.contains("required"))
            || l.contains("http error 403")
            || l.contains("this video is not available")
            || l.contains("video unavailable")
            || l.contains("confirm you're not a bot")
            || l.contains("confirm you are not a bot");
    }

    private List<String> buildCommand(long folderId, String url, String password) {
        List<String> cmd = new ArrayList<>(List.of(
            global.getPythonPath(),
            global.resolve(config.getScriptName()),
            url,
            "--folder-id", String.valueOf(folderId),
            "--max-height", String.valueOf(config.getMaxHeight())
        ));
        if (password != null && !password.isBlank()) {
            cmd.add("--password");
            cmd.add(password);
        }
        return cmd;
    }
}
