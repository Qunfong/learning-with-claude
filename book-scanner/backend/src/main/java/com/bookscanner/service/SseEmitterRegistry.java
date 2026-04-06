package com.bookscanner.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for active SSE connections, indexed by uploadId.
 *
 * Learning note: ConcurrentHashMap is thread-safe. SSE emitters are created
 * by the HTTP request thread and written to by the Kafka consumer thread.
 * Without thread-safety we would have race conditions.
 *
 * In production you would replace this with a distributed solution
 * (e.g. Redis pub/sub) so it works across multiple backend instances.
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
        log.debug("SSE emitter registered for uploadId: {}", uploadId);
    }

    public SseEmitter get(String uploadId) {
        return emitters.get(uploadId);
    }

    public void remove(String uploadId) {
        emitters.remove(uploadId);
    }
}
