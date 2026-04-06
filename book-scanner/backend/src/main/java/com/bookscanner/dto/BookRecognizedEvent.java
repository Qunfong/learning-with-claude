package com.bookscanner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kafka event dat wordt gepubliceerd op het 'book.recognized' topic.
 * Status kan zijn: PROCESSING, RECOGNIZED, FAILED.
 *
 * Bij RECOGNIZED bevat candidates de top-3 gevonden boeken.
 * Bij FAILED geeft errorCode de reden aan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookRecognizedEvent {

    public enum Status {
        PROCESSING, RECOGNIZED, FAILED
    }

    public enum ErrorCode {
        NO_TEXT_DETECTED, OCR_SERVICE_ERROR, BOOK_NOT_FOUND
    }

    private String uploadId;
    private Status status;
    private ErrorCode errorCode;        // alleen bij FAILED

    // Top-3 boekresultaten bij RECOGNIZED
    private List<BookCandidate> candidates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookCandidate {
        private String title;
        private String author;
        private String isbn;
        private String coverUrl;
        private String description;
    }
}
