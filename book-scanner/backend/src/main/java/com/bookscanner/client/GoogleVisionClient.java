package com.bookscanner.client;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Wrapper rond de Google Cloud Vision API voor OCR (text detection).
 *
 * Leerpunt: We gebruiken DOCUMENT_TEXT_DETECTION in plaats van TEXT_DETECTION
 * omdat dit beter werkt voor dichte tekst op boekomslagen. Het geeft ook
 * een gestructureerde output met woorden en regels.
 *
 * Let op: als GOOGLE_VISION_API_KEY leeg is, gooit deze client een exception.
 * In dat geval geeft de ImageProcessingService een FAILED event terug.
 */
@Component
@Slf4j
public class GoogleVisionClient {

    @Value("${app.google.vision.api-key:}")
    private String apiKey;

    /**
     * Voert OCR uit op de afbeelding en geeft de gedetecteerde tekst terug.
     *
     * @param imagePath pad naar de afbeelding op disk
     * @return Optional met de gedetecteerde tekst, leeg als niets gevonden
     */
    public Optional<String> detectText(Path imagePath) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Google Vision API key is niet geconfigureerd. " +
                    "Stel GOOGLE_VISION_API_KEY in als environment variabele.");
        }

        byte[] imageBytes = Files.readAllBytes(imagePath);
        ByteString imgBytes = ByteString.copyFrom(imageBytes);

        Image image = Image.newBuilder().setContent(imgBytes).build();
        Feature feature = Feature.newBuilder()
                .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
                .build();
        AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feature)
                .setImage(image)
                .build();

        // ImageAnnotatorClient gebruikt de GOOGLE_APPLICATION_CREDENTIALS env var
        // of de API key die we meegeven via de builder
        ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                .build();

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create(settings)) {
            BatchAnnotateImagesResponse batchResponse =
                    client.batchAnnotateImages(List.of(request));

            AnnotateImageResponse response = batchResponse.getResponsesList().get(0);

            if (response.hasError()) {
                log.warn("Vision API fout: {}", response.getError().getMessage());
                return Optional.empty();
            }

            String fullText = response.getFullTextAnnotation().getText();
            if (fullText == null || fullText.isBlank()) {
                return Optional.empty();
            }

            log.debug("OCR resultaat: {}", fullText);
            return Optional.of(fullText.trim());
        }
    }
}
