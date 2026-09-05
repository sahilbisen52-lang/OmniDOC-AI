package com.docassistant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health-check controller exposing application status, version, and uptime.
 *
 * <p>Designed for use by container orchestrators (Kubernetes liveness/readiness
 * probes), monitoring dashboards, and frontend connectivity checks.</p>
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Application health and status information")
public class HealthController {

    private static final Instant START_TIME = Instant.now();

    /**
     * Returns the current application health status, version, and uptime.
     *
     * @return a JSON map with health information
     */
    @GetMapping
    @Operation(summary = "Health check",
               description = "Returns application status, version, and uptime for monitoring.")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "doc-assistant-backend");
        health.put("version", "1.0.0-SNAPSHOT");
        health.put("timestamp", Instant.now().toString());

        // Calculate uptime
        Duration uptime = Duration.between(START_TIME, Instant.now());
        health.put("uptime", formatDuration(uptime));
        health.put("uptimeSeconds", uptime.toSeconds());

        // JVM info
        Map<String, Object> jvm = new LinkedHashMap<>();
        Runtime runtime = Runtime.getRuntime();
        jvm.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        jvm.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        jvm.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        jvm.put("availableProcessors", runtime.availableProcessors());
        jvm.put("javaVersion", System.getProperty("java.version"));
        health.put("jvm", jvm);

        return ResponseEntity.ok(health);
    }

    /**
     * Formats a {@link Duration} into a human-readable string.
     */
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
        }
        return String.format("%ds", seconds);
    }
}
