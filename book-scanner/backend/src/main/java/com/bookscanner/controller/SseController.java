package com.bookscanner.controller;

import com.bookscanner.service.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE endpoint through which the frontend receives real-time updates
 * about the processing of an uploaded image.
 *
 * Usage:
 *   GET /api/sse/status/{uploadId}
 *   Accept: text/event-stream
 *
 * Learning note: SseEmitter keeps the HTTP connection open. Spring MVC handles
 * this asynchronously via a separate thread pool. The 3-minute timeout is
 * generous enough for the processing time, but prevents connections from
 * staying open indefinitely if the client disappears.
 */
@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
@Slf4j
public class SseController {

    // 3 minuten timeout — ruim genoeg voor OCR + Open Library lookup
    private static final long SSE_TIMEOUT_MS = 3 * 60 * 1000L;

    private final SseEmitterRegistry sseEmitterRegistry;

    @GetMapping(value = "/status/{uploadId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStatus(@PathVariable String uploadId) {
        log.info("SSE verbinding geopend voor uploadId: {}", uploadId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sseEmitterRegistry.register(uploadId, emitter);

        return emitter;
    }
}
