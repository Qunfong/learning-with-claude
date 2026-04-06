package com.bookscanner.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry voor actieve SSE verbindingen, geïndexeerd op uploadId.
 *
 * Leerpunt: ConcurrentHashMap is thread-safe. SSE emitters worden aangemaakt
 * door de HTTP request thread en beschreven door de Kafka consumer thread.
 * Zonder thread-safety zouden we race conditions krijgen.
 *
 * In productie zou je dit vervangen door een gedistribueerde oplossing
 * (bijv. Redis pub/sub) zodat het werkt over meerdere backend instances.
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void register(String uploadId, SseEmitter emitter) {
        emitters.put(uploadId, emitter);
        emitter.onCompletion(() -> emitters.remove(uploadId));
        emitter.onTimeout(() -> emitters.remove(uploadId));
        emitter.onError(e -> emitters.remove(uploadId));
        log.debug("SSE emitter geregistreerd voor uploadId: {}", uploadId);
    }

    public SseEmitter get(String uploadId) {
        return emitters.get(uploadId);
    }

    public void remove(String uploadId) {
        emitters.remove(uploadId);
    }
}
