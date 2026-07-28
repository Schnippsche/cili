package de.toengi.cili.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThatCode;

class AsyncConfigTest {

    @Test
    void mailExecutor_rejectedExecutionHandler_logsInsteadOfThrowing() {
        // Bewusst kein Choreographieren von Threads/Queue-Fuellstand über echte Nebenläufigkeit:
        // ThreadPoolExecutor wächst erst über corePoolSize hinaus, wenn queue.offer() fehlschlägt —
        // ein zeitbasierter "Queue voll"-Aufbau ist fehleranfällig und nicht deterministisch.
        // Stattdessen wird der konfigurierte RejectedExecutionHandler direkt aufgerufen, exakt wie
        // ihn ein ThreadPoolExecutor bei einer echten Ablehnung aufrufen würde.
        AsyncConfig config = new AsyncConfig();
        ThreadPoolTaskExecutor exec = (ThreadPoolTaskExecutor) config.mailExecutor();

        assertThatCode(() ->
            exec.getThreadPoolExecutor().getRejectedExecutionHandler()
                .rejectedExecution(() -> {}, exec.getThreadPoolExecutor())
        ).doesNotThrowAnyException();

        exec.shutdown();
    }
}
