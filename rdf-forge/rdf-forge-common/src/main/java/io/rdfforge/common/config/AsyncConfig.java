package io.rdfforge.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for async task execution.
 * Provides dedicated executor for audit logging to avoid impacting
 * application performance.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Executor for audit logging operations.
     * Uses a dedicated thread pool to ensure audit logging doesn't
     * block or slow down main application threads.
     */
    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}
