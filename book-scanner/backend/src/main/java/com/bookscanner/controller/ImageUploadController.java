package com.bookscanner.controller;

import com.bookscanner.service.ImageUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for uploading book images.
 *
 * POST /api/images/upload
 *   - Accepts multipart/form-data with field 'file'
 *   - Validates file size (max 10MB via Spring config) and type (JPEG/PNG)
 *   - Returns 202 Accepted + uploadId that can be used to follow progress via SSE
 */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Slf4j
public class ImageUploadController {

    private static final List<String> ALLOWED_TYPES = List.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/jpg"
    );

    private final ImageUploadService imageUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("error", "Only JPEG and PNG files are allowed"));
        }

        // File size is already handled by Spring's multipart limit (413),
        // but we add an explicit check for a clearer error message
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty"));
        }

        try {
            String uploadId = imageUploadService.uploadAndSubmit(file);
            return ResponseEntity.accepted()
                    .body(Map.of(
                            "uploadId", uploadId,
                            "message", "Image received, processing started"
                    ));
        } catch (IOException e) {
            log.error("Error saving image", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process image"));
        }
    }
}
