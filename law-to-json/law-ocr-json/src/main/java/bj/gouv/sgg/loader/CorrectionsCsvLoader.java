package bj.gouv.sgg.loader;

import bj.gouv.sgg.entity.ErrorCorrection;
import bj.gouv.sgg.repository.ErrorCorrectionRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class CorrectionsCsvLoader {

    private final ErrorCorrectionRepository errorCorrectionRepository;

    public CorrectionsCsvLoader(ErrorCorrectionRepository errorCorrectionRepository) {
        this.errorCorrectionRepository = errorCorrectionRepository;
    }

    @PostConstruct
    public void loadCorrectionsFromCsv() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("corrections.csv")) {
            if (is == null) {
                log.warn("⚠️ corrections.csv not found in resources");
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                int lineNumber = 0;

                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    parseAndSaveCorrection(line, lineNumber);
                }
            }
        } catch (Exception e) {
            log.error("❌ Failed to load corrections.csv: {}", e.getMessage(), e);
        }
    }

    private void parseAndSaveCorrection(String line, int lineNumber) {
        line = line.trim();

        // Skip empty lines and comments
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }

        // Parse correction line (format: wrong,correct)
        if (!line.contains(",")) {
            log.warn("⚠️ Invalid correction format at line {}: {}", lineNumber, line);
            return;
        }

        String[] parts = line.split(",", 2);
        if (parts.length != 2) {
            log.warn("⚠️ Invalid correction format at line {}: {}", lineNumber, line);
            return;
        }

        String errorFound = parts[0].trim();
        String errorCorrection = parts[1].trim();

        if (!errorFound.isEmpty() && !errorCorrection.isEmpty()) {
            if (errorCorrectionRepository.findByErrorFound(errorFound).isEmpty()) {
                errorCorrectionRepository.save(new ErrorCorrection(null, errorFound, 0, errorCorrection));
                log.info("✅ Saved correction: '{}' → '{}'", errorFound, errorCorrection);
            }
        }
    }
}