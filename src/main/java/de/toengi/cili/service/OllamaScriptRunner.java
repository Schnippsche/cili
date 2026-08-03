package de.toengi.cili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.OllamaConfig;
import de.toengi.cili.util.PythonProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Führt analyze_worker.py mit einem beliebigen Prompt aus und entlädt das Ollama-Modell danach aus dem VRAM. */
@Component
@Slf4j
public class OllamaScriptRunner {

    private final CiliGlobalConfig global;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public OllamaScriptRunner(CiliGlobalConfig global) {
        this.global = global;
    }

    public String run(String textContent, String promptFileName, OllamaConfig config)
            throws IOException, InterruptedException {
        Path textFile = Files.createTempFile("ollama-text-", ".txt");
        try {
            Files.writeString(textFile, textContent, StandardCharsets.UTF_8);

            List<String> cmd = List.of(
                    global.getPythonPath(),
                    global.resolve(config.getScriptName()),
                    "--text-file",   textFile.toString(),
                    "--prompt-file", global.resolve(promptFileName),
                    "--model",       config.getModel(),
                    "--url",         config.getUrl(),
                    "--timeout",     String.valueOf(config.getTimeoutMinutes() * 60),
                    "--num-ctx",     String.valueOf(config.getNumCtx())
            );

            log.info("Starte analyze_worker: {}", cmd);
            ProcessBuilder pb = PythonProcessUtils.forScript(cmd, global.resolve(config.getScriptName()));
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            Thread errThread = new Thread(() -> {
                try (var r = new BufferedReader(new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                    r.lines().forEach(line -> log.info("[analyze] {}", line));
                } catch (IOException ignored) {}
            });
            errThread.setDaemon(true);
            errThread.start();

            byte[][] stdoutHolder = {null};
            Thread stdoutThread = new Thread(() -> {
                try { stdoutHolder[0] = proc.getInputStream().readAllBytes(); }
                catch (IOException ignored) {}
            });
            stdoutThread.setDaemon(true);
            stdoutThread.start();

            boolean finished = proc.waitFor(config.getTimeoutMinutes(), TimeUnit.MINUTES);
            if (!finished) {
                proc.destroyForcibly();
                throw new IOException("analyze_worker.py Timeout nach " + config.getTimeoutMinutes() + " Minuten");
            }
            if (proc.exitValue() != 0) {
                throw new IOException("analyze_worker.py Exit-Code " + proc.exitValue());
            }
            stdoutThread.join(config.getTimeoutMinutes() * 60_000L);
            return stdoutHolder[0] != null ? new String(stdoutHolder[0], StandardCharsets.UTF_8).trim() : "";

        } finally {
            Files.deleteIfExists(textFile);
        }
    }

    public void unloadModel(OllamaConfig config) {
        try {
            String body = toJson(Map.of("model", config.getModel(), "keep_alive", 0));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl() + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            log.info("Ollama-Modell '{}' aus VRAM entladen (keep_alive=0 gesendet)", config.getModel());
            waitForVramReleased(config);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Konnte Ollama-Modell nicht entladen: {}", e.getMessage());
        }
    }

    private void waitForVramReleased(OllamaConfig config) throws InterruptedException {
        HttpRequest psReq = HttpRequest.newBuilder()
                .uri(URI.create(config.getUrl() + "/api/ps"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        for (int i = 0; i < 15; i++) {
            Thread.sleep(2_000);
            try {
                HttpResponse<String> resp = httpClient.send(psReq, HttpResponse.BodyHandlers.ofString());
                if (!resp.body().contains(config.getModel())) {
                    log.info("Ollama-Modell '{}' nach {}s aus VRAM entfernt", config.getModel(), (i + 1) * 2);
                    return;
                }
            } catch (Exception e) {
                log.debug("Ollama /api/ps nicht erreichbar: {}", e.getMessage());
                return;
            }
        }
        log.warn("Ollama-Modell '{}' nach 30s noch im VRAM — nächster Job startet trotzdem", config.getModel());
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }
}
