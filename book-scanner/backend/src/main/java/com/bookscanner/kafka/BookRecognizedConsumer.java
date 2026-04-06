package com.bookscanner.kafka;

import com.bookscanner.dto.BookRecognizedEvent;
import com.bookscanner.service.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Consumes events from the 'book.recognized' topic and forwards them
 * to the waiting SSE connection of the frontend.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookRecognizedConsumer {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.book-recognized}",
            containerFactory = "bookRecognizedListenerFactory"
    )
    public void onBookRecognized(String payload) {
        BookRecognizedEvent event;
        try {
            event = objectMapper.readValue(payload, BookRecognizedEvent.class);
        } catch (Exception e) {
            log.error("Kon BookRecognizedEvent niet parsen: {}", payload, e);
            return;
        }

        String uploadId = event.getUploadId();
        log.info("BookRecognized event ontvangen voor uploadId: {}, status: {}",
                uploadId, event.getStatus());

        SseEmitter emitter = sseEmitterRegistry.get(uploadId);
        if (emitter == null) {
            log.debug("Geen actieve SSE emitter voor uploadId: {}", uploadId);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("book-status")
                    .data(event));

            if (event.getStatus() != BookRecognizedEvent.Status.PROCESSING) {
                emitter.complete();
                sseEmitterRegistry.remove(uploadId);
            }
        } catch (IOException e) {
            log.warn("Kon SSE event niet sturen voor uploadId: {}", uploadId, e);
            sseEmitterRegistry.remove(uploadId);
        }
    }
}
