package de.toengi.cili.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
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
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(1);
        exec.setQueueCapacity(1);
        exec.setThreadNamePrefix("telegram-import-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(120);
        exec.initialize();
        return exec;
    }

}
