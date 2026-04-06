package com.bookscanner.controller;

import com.bookscanner.service.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE endpoint waarmee de frontend realtime updates ontvangt
 * over de verwerking van een geüploade afbeelding.
 *
 * Gebruik:
 *   GET /api/sse/status/{uploadId}
 *   Accept: text/event-stream
 *
 * Leerpunt: SseEmitter houdt de HTTP verbinding open. Spring MVC behandelt
 * dit asynchroon via een apart thread pool. De timeout van 3 minuten is
 * ruim genoeg voor de verwerkingstijd, maar voorkomt dat verbindingen
 * eeuwig open blijven als de client verdwijnt.
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
