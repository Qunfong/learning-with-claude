package com.bookscanner.service;

import com.bookscanner.client.GoogleVisionClient;
import com.bookscanner.client.OpenLibraryClient;
import com.bookscanner.dto.BookRecognizedEvent;
import com.bookscanner.dto.BookRecognizedEvent.BookCandidate;
import com.bookscanner.dto.BookRecognizedEvent.ErrorCode;
import com.bookscanner.dto.BookRecognizedEvent.Status;
import com.bookscanner.dto.ImageSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Hart van de streaming pipeline.
 *
 * Stroom:
 *   [Kafka: image.submitted]
 *      → OCR via Google Vision API
 *      → Zoek boek op via Open Library API
 *      → Publiceer resultaat op [Kafka: book.recognized]
 *
 * Leerpunt: @KafkaListener maakt van deze methode een message-driven consumer.
 * Spring Kafka start automatisch een thread die wacht op berichten.
 * containerFactory verwijst naar de bean in KafkaConsumerConfig.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageProcessingService {

    private final GoogleVisionClient visionClient;
    private final OpenLibraryClient openLibraryClient;
    private final KafkaTemplate<String, String> bookRecognizedKafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.book-recognized}")
    private String bookRecognizedTopic;

    @KafkaListener(
            topics = "${app.kafka.topics.image-submitted}",
            containerFactory = "imageSubmittedListenerFactory"
    )
    public void processImage(String payload) {
        ImageSubmittedEvent event;
        try {
            event = objectMapper.readValue(payload, ImageSubmittedEvent.class);
        } catch (Exception e) {
            log.error("Kon ImageSubmittedEvent niet parsen: {}", payload, e);
            return;
        }
        String uploadId = event.getUploadId();
        log.info("Verwerking gestart voor uploadId: {}", uploadId);

        // Stap 1: OCR
        Optional<String> detectedText;
        try {
            Path imagePath = Paths.get(event.getFilePath());
            detectedText = visionClient.detectText(imagePath);
        } catch (IllegalStateException e) {
            log.error("Vision API niet geconfigureerd: {}", e.getMessage());
            publishFailed(uploadId, ErrorCode.OCR_SERVICE_ERROR);
            return;
        } catch (Exception e) {
            log.error("OCR fout voor uploadId {}: {}", uploadId, e.getMessage(), e);
            publishFailed(uploadId, ErrorCode.OCR_SERVICE_ERROR);
            return;
        }

        if (detectedText.isEmpty()) {
            log.info("Geen tekst gevonden op afbeelding voor uploadId: {}", uploadId);
            publishFailed(uploadId, ErrorCode.NO_TEXT_DETECTED);
            return;
        }

        // Stap 2: Zoek boek op (top 3 kandidaten)
        String query = extractSearchQuery(detectedText.get());
        List<BookCandidate> candidates = openLibraryClient.search(query);

        if (candidates.isEmpty()) {
            log.info("Geen boek gevonden voor query '{}' (uploadId: {})", query, uploadId);
            publishFailed(uploadId, ErrorCode.BOOK_NOT_FOUND);
            return;
        }

        // Stap 3: Publiceer resultaat
        BookRecognizedEvent resultEvent = BookRecognizedEvent.builder()
                .uploadId(uploadId)
                .status(Status.RECOGNIZED)
                .candidates(candidates)
                .build();

        try {
            bookRecognizedKafkaTemplate.send(bookRecognizedTopic, uploadId,
                    objectMapper.writeValueAsString(resultEvent));
        } catch (Exception e) {
            log.error("Kon resultaat niet publiceren voor uploadId: {}", uploadId, e);
        }
        log.info("Boek herkend voor uploadId: {}, {} kandidaat(en)", uploadId, candidates.size());
    }

    /**
     * Extraheert een zoekopdracht uit de OCR tekst.
     * Neemt de eerste 100 tekens om de API query beknopt te houden.
     * In een productie-implementatie zou je NLP gebruiken voor betere extractie.
     */
    private String extractSearchQuery(String ocrText) {
        String cleaned = ocrText.replaceAll("[\\n\\r]+", " ").trim();
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }

    private void publishFailed(String uploadId, ErrorCode errorCode) {
        BookRecognizedEvent failedEvent = BookRecognizedEvent.builder()
                .uploadId(uploadId)
                .status(Status.FAILED)
                .errorCode(errorCode)
                .build();
        try {
            bookRecognizedKafkaTemplate.send(bookRecognizedTopic, uploadId,
                    objectMapper.writeValueAsString(failedEvent));
        } catch (Exception e) {
            log.error("Kon failed event niet publiceren voor uploadId: {}", uploadId, e);
        }
    }
}
