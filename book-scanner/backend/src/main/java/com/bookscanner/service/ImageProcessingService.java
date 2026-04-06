package com.bookscanner.service;

import com.bookscanner.client.TesseractOcrClient;
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
 * Core of the streaming pipeline.
 *
 * Flow:
 *   [Kafka: image.submitted]
 *      → OCR via Tesseract
 *      → Look up book via Open Library API
 *      → Publish result to [Kafka: book.recognized]
 *
 * Learning note: @KafkaListener turns this method into a message-driven consumer.
 * Spring Kafka automatically starts a thread that waits for incoming messages.
 * containerFactory refers to the bean defined in KafkaConsumerConfig.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageProcessingService {

    private final TesseractOcrClient visionClient;
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
            log.error("Failed to parse ImageSubmittedEvent: {}", payload, e);
            return;
        }
        String uploadId = event.getUploadId();
        log.info("Processing started for uploadId: {}", uploadId);

        // Step 1: OCR
        Optional<String> detectedText;
        try {
            Path imagePath = Paths.get(event.getFilePath());
            detectedText = visionClient.detectText(imagePath);
        } catch (Exception e) {
            log.error("OCR error for uploadId {}: {}", uploadId, e.getMessage(), e);
            publishFailed(uploadId, ErrorCode.OCR_SERVICE_ERROR);
            return;
        }

        if (detectedText.isEmpty()) {
            log.info("No text detected in image for uploadId: {}", uploadId);
            publishFailed(uploadId, ErrorCode.NO_TEXT_DETECTED);
            return;
        }

        // Step 2: Look up book (top 3 candidates)
        String query = extractSearchQuery(detectedText.get());
        List<BookCandidate> candidates = openLibraryClient.search(query);

        if (candidates.isEmpty()) {
            log.info("No book found for query '{}' (uploadId: {})", query, uploadId);
            publishFailed(uploadId, ErrorCode.BOOK_NOT_FOUND);
            return;
        }

        // Step 3: Publish result
        BookRecognizedEvent resultEvent = BookRecognizedEvent.builder()
                .uploadId(uploadId)
                .status(Status.RECOGNIZED)
                .candidates(candidates)
                .build();

        try {
            bookRecognizedKafkaTemplate.send(bookRecognizedTopic, uploadId,
                    objectMapper.writeValueAsString(resultEvent));
        } catch (Exception e) {
            log.error("Failed to publish result for uploadId: {}", uploadId, e);
        }
        log.info("Book recognized for uploadId: {}, {} candidate(s)", uploadId, candidates.size());
    }

    /**
     * Extracts a search query from the OCR text.
     * Takes the first 100 characters to keep the API query concise.
     * In a production implementation you would use NLP for better extraction.
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
            log.error("Failed to publish failed event for uploadId: {}", uploadId, e);
        }
    }
}
