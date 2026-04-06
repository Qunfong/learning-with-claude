package com.bookscanner.service;

import com.bookscanner.dto.ImageSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.image-submitted}")
    private String imageSubmittedTopic;

    @Value("${app.upload.tmp-dir}")
    private String uploadTmpDir;

    public String uploadAndSubmit(MultipartFile file) throws IOException {
        String uploadId = UUID.randomUUID().toString();

        Path uploadDir = Paths.get(uploadTmpDir);
        Files.createDirectories(uploadDir);

        String extension = getExtension(file.getOriginalFilename());
        Path filePath = uploadDir.resolve(uploadId + extension);
        file.transferTo(filePath.toFile());

        log.info("Afbeelding opgeslagen: {} (uploadId: {})", filePath, uploadId);

        ImageSubmittedEvent event = new ImageSubmittedEvent(
                uploadId,
                filePath.toString(),
                file.getContentType()
        );
        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(imageSubmittedTopic, uploadId, payload);

        log.info("Event gepubliceerd op topic '{}' voor uploadId: {}", imageSubmittedTopic, uploadId);

        return uploadId;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}
