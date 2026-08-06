package com.bookscanner.client;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * OCR client based on Tesseract — fully free and runs locally.
 *
 * Tesseract is the most widely used open-source OCR engine (Google Research).
 * Tess4J is the Java wrapper around it.
 *
 * Learning note: Tesseract works best with:
 * - High-resolution images (300+ DPI)
 * - Straight text (not skewed or curved)
 * - High contrast (black text on white background)
 * Book covers are often colorful and complex — results may vary.
 * For better results, you could add a pre-processing step
 * (grayscale, increase contrast) using e.g. ImageIO or OpenCV.
 *
 * In Docker: Tesseract + language data are installed via the Dockerfile.
 * The datapath points to /usr/share/tessdata (standard Linux location).
 */
@Component
@Slf4j
public class TesseractOcrClient {

    @Value("${app.tesseract.data-path:/usr/share/tessdata}")
    private String tessDataPath;

    @Value("${app.tesseract.language:eng+nld}")
    private String language;

    /**
     * Performs OCR on an image and returns the recognized text.
     *
     * @param imagePath path to the image file
     * @return recognized text, empty if nothing found or on error
     */
    public Optional<String> detectText(Path imagePath) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath);
        tesseract.setLanguage(language);

        // PSM 3 = automatic page segmentation (default, works well for book covers)
        tesseract.setPageSegMode(3);

        // OEM 1 = LSTM neural net mode (most accurate in Tesseract 4+)
        tesseract.setOcrEngineMode(1);

        try {
            String result = tesseract.doOCR(imagePath.toFile());
            if (result == null || result.isBlank()) {
                log.info("Tesseract: no text found in {}", imagePath.getFileName());
                return Optional.empty();
            }
            log.debug("Tesseract result: {}", result.trim());
            return Optional.of(result.trim());
        } catch (TesseractException e) {
            log.error("Tesseract OCR error for {}: {}", imagePath.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }
}
