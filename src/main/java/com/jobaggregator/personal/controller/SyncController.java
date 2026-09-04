package com.jobaggregator.personal.controller;

import com.jobaggregator.personal.dto.SyncResultDto;
import com.jobaggregator.personal.service.JobSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@Slf4j
public class SyncController {

    private final JobSyncService jobSyncService;

    @org.springframework.beans.factory.annotation.Value("${jobs.sync.secret.token:${SYNC_SECRET_TOKEN:}}")
    private String syncSecretToken;

    private final java.util.concurrent.atomic.AtomicLong lastSyncTimestamp = new java.util.concurrent.atomic.AtomicLong(0);
    private static final long COOLDOWN_MILLIS = 30_000; // 30 segundos entre sincronizaciones manuales

    @PostMapping
    public ResponseEntity<SyncResultDto> triggerManualSync(
            jakarta.servlet.http.HttpServletRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Sync-Token", required = false) String headerToken,
            @org.springframework.web.bind.annotation.RequestParam(value = "token", required = false) String paramToken) {

        // 1. Verificación de token si está configurado en variables de entorno.
        // Si la petición proviene directamente de la interfaz web (same-origin o referer coincidente), se permite sin exigir token en el navegador.
        boolean isSameOrigin = request != null && (
                "same-origin".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site")) ||
                (request.getHeader("Referer") != null && request.getHeader("Host") != null && request.getHeader("Referer").contains(request.getHeader("Host")))
        );

        if (!isSameOrigin && syncSecretToken != null && !syncSecretToken.isBlank()) {
            String token = headerToken != null ? headerToken : paramToken;
            if (token == null || !syncSecretToken.trim().equals(token.trim())) {
                log.warn("Unauthorized external manual sync attempt.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        SyncResultDto.builder()
                                .success(false)
                                .message("No autorizado: Token de sincronización inválido o ausente.")
                                .timestamp(LocalDateTime.now())
                                .build()
                );
            }
        }

        // 2. Protección de Rate-Limit / Cooldown
        long now = System.currentTimeMillis();
        long last = lastSyncTimestamp.get();
        if (now - last < COOLDOWN_MILLIS) {
            long remainingSecs = (COOLDOWN_MILLIS - (now - last)) / 1000;
            log.warn("Sync rate limit hit. Remaining cooldown: {} seconds.", remainingSecs);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    SyncResultDto.builder()
                            .success(false)
                            .message("Sincronización en curso o ejecutada recientemente. Espera " + remainingSecs + "s para volver a sincronizar.")
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }

        lastSyncTimestamp.set(now);

        try {
            SyncResultDto result = jobSyncService.syncAll();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Fatal error during manual sync execution: ", e);
            SyncResultDto errorDto = SyncResultDto.builder()
                    .success(false)
                    .message("Error durante la sincronización: " + e.getMessage())
                    .totalFetched(0)
                    .newSaved(0)
                    .skippedDuplicates(0)
                    .fetchedBySource(Collections.emptyMap())
                    .sourceStatus(Collections.singletonMap("ERROR", e.getMessage()))
                    .durationMs(0)
                    .timestamp(LocalDateTime.now())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDto);
        }
    }
}
