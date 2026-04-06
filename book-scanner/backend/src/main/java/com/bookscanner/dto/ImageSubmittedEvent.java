package com.bookscanner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event dat wordt gepubliceerd op het 'image.submitted' topic.
 * Bevat het pad naar het tijdelijk opgeslagen afbeeldingsbestand en
 * een uniek ID waarmee de frontend via SSE de status kan bijhouden.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageSubmittedEvent {
    private String uploadId;
    private String filePath;
    private String contentType;
}
