package com.jobaggregator.personal.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    /**
     * Endpoint ultrarrápido para keep-alive con UptimeRobot (evita suspensión en Render).
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong - " + System.currentTimeMillis());
    }

    /**
     * Endpoint con información de salud del sistema, profile y uso de memoria.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "personal-job-aggregator");
        health.put("profile", activeProfile);
        health.put("timestamp", LocalDateTime.now());

        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        Map<String, Object> memStats = new HashMap<>();
        memStats.put("usedMB", usedMemory);
        memStats.put("maxMB", maxMemory);
        memStats.put("freeMB", freeMemory);

        health.put("memory", memStats);

        return ResponseEntity.ok(health);
    }
}
