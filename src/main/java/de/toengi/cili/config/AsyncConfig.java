package de.toengi.cili.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class AsyncConfig {

    /** Anzahl gleichzeitiger Video-Workflows (Transcode + WAV + Whisper). */
    @Value("${cili.processing.video-concurrency:1}")
    private int videoConcurrency;

    @Bean(name = "thumbnailExecutor")
    public Executor thumbnailExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("thumbnail-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        exec.initialize();
        return exec;
    }

    @Bean(name = "processingExecutor")
    public Executor processingExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(videoConcurrency);
        exec.setMaxPoolSize(Math.max(videoConcurrency, 2));
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("processing-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(60);
        exec.initialize();
        return exec;
    }

    @Bean(name = "transcodeExecutor")
    public Executor transcodeExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(videoConcurrency);
        exec.setMaxPoolSize(videoConcurrency);
        exec.setQueueCapacity(500);
        exec.setThreadNamePrefix("transcode-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(300);
        exec.initialize();
        return exec;
    }

    /** Einziger Executor für alle GPU-intensiven Jobs (Whisper, NLLB, Ollama) — 1 Thread garantiert serielle Ausführung. */
    @Bean(name = "gpuExecutor")
    public Executor gpuExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(1);
        exec.setQueueCapacity(500);
        exec.setThreadNamePrefix("gpu-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(900);
        exec.initialize();
        return exec;
    }

    @Bean(name = "videoImportExecutor")
    public Executor videoImportExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(2);
        exec.setQueueCapacity(20);
        exec.setThreadNamePrefix("video-import-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(120);
        exec.initialize();
        return exec;
    }

    @Bean(name = "telegramExecutor")
    public Executor telegramExecutor() {
        // maxPoolSize bleibt bei 1: alle drei Telegram-Skripte (telegram_import_mensch.py,
        // _tier.py, _webinare.py) teilen sich eine einzige Telethon-Session-Datei
        // (scripts/telegram_session), die nicht
        // gleichzeitig aus mehreren Prozessen geöffnet werden darf. queueCapacity muss
        // aber über der Quellenzahl liegen, sonst wird ein Trigger bei gleichzeitiger
        // Auslastung mit TaskRejectedException verworfen, nachdem der ProcessingJob
        // schon als PENDING gespeichert wurde — der Job bliebe dann für immer hängen.
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(1);
        exec.setQueueCapacity(16);
        exec.setThreadNamePrefix("telegram-import-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(120);
        exec.initialize();
        return exec;
    }

    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(2);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("mail-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        // Standard-AbortPolicy würde TaskRejectedException SYNCHRON beim Aufrufer werfen
        // (vor Ausführung von MailService.send, also außerhalb von dessen try/catch) —
        // widerspricht dem Fire-and-Forget-Anspruch. Stattdessen loggen und verwerfen.
        exec.setRejectedExecutionHandler((Runnable task, ThreadPoolExecutor executor) ->
            log.warn("Mail-Executor-Queue voll — E-Mail-Auftrag wurde verworfen"));
        exec.initialize();
        return exec;
    }

}
