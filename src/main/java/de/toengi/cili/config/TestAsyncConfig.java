package de.toengi.cili.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@Profile("test")
public class TestAsyncConfig {

    @Bean(name = "thumbnailExecutor") @Primary
    public Executor thumbnailExecutor() { return minimal("test-thumb-"); }

    @Bean(name = "processingExecutor") @Primary
    public Executor processingExecutor() { return minimal("test-proc-"); }

    @Bean(name = "transcodeExecutor") @Primary
    public Executor transcodeExecutor() { return minimal("test-transcode-"); }

    @Bean(name = "gpuExecutor") @Primary
    public Executor gpuExecutor() { return task -> {}; }

    private static Executor minimal(String prefix) {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(1);
        exec.setQueueCapacity(10);
        exec.setThreadNamePrefix(prefix);
        exec.setWaitForTasksToCompleteOnShutdown(false);
        exec.setAwaitTerminationSeconds(1);
        exec.initialize();
        return exec;
    }
}
