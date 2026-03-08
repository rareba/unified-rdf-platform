package io.rdfforge.job.config;

import io.rdfforge.job.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration for scheduled job timeout handling.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class JobTimeoutConfig {

    private final JobService jobService;

    /**
     * Check for and handle timed out jobs every 5 minutes.
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void checkJobTimeouts() {
        log.debug("Running scheduled job timeout check");
        try {
            jobService.handleJobTimeouts();
        } catch (Exception e) {
            log.error("Error during job timeout check", e);
        }
    }
}
