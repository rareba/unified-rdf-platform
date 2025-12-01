package io.rdfforge.common.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom health indicator for RDF Forge services.
 * Provides detailed health information including memory usage and uptime.
 */
@Slf4j
@Component
public class RdfForgeHealthIndicator implements HealthIndicator {

    private final Instant startTime = Instant.now();
    
    @Value("${spring.application.name:rdf-forge}")
    private String applicationName;
    
    @Value("${spring.application.version:1.0.0}")
    private String applicationVersion;

    @Override
    public Health health() {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            
            // Application info
            details.put("application", applicationName);
            details.put("version", applicationVersion);
            
            // Uptime
            Duration uptime = Duration.between(startTime, Instant.now());
            details.put("uptime", formatDuration(uptime));
            details.put("uptimeSeconds", uptime.toSeconds());
            
            // Memory info
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long heapMax = memoryBean.getHeapMemoryUsage().getMax();
            double heapUsagePercent = (double) heapUsed / heapMax * 100;
            
            Map<String, Object> memory = new LinkedHashMap<>();
            memory.put("heapUsed", formatBytes(heapUsed));
            memory.put("heapMax", formatBytes(heapMax));
            memory.put("heapUsagePercent", String.format("%.1f%%", heapUsagePercent));
            memory.put("nonHeapUsed", formatBytes(memoryBean.getNonHeapMemoryUsage().getUsed()));
            details.put("memory", memory);
            
            // Runtime info
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            details.put("javaVersion", runtimeBean.getSpecVersion());
            details.put("vmName", runtimeBean.getVmName());
            
            // Thread info
            int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
            details.put("threadCount", threadCount);
            
            // Determine health status based on memory usage
            if (heapUsagePercent > 90) {
                return Health.down()
                    .withDetails(details)
                    .withDetail("warning", "Heap memory usage above 90%")
                    .build();
            } else if (heapUsagePercent > 80) {
                return Health.status("WARNING")
                    .withDetails(details)
                    .withDetail("warning", "Heap memory usage above 80%")
                    .build();
            }
            
            return Health.up()
                .withDetails(details)
                .build();
                
        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down()
                .withException(e)
                .build();
        }
    }
    
    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        
        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), unit);
    }
}