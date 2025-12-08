package bj.gouv.sgg.impl;

import bj.gouv.sgg.service.CorrectOcrText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implémentation des corrections OCR depuis corrections.csv
 */
@Slf4j
@Component
public class CsvCorrector implements CorrectOcrText {

    private final Map<String, String> corrections;

    public CsvCorrector() {
        this.corrections = loadCorrections();
        log.info("✅ Loaded {} OCR corrections from corrections.csv", corrections.size());
    }

    /**
     * Charge les corrections depuis corrections.csv dans les resources
     */
    private Map<String, String> loadCorrections() {
        Map<String, String> correctionMap = new LinkedHashMap<>();
        
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("corrections.csv")) {
            assert is != null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    line = line.trim();

                    // Skip empty lines and comments
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    // Parse correction line (format: wrong,correct)
                    if (line.contains(",")) {
                        String[] parts = line.split(",", 2);
                        if (parts.length == 2) {
                            String wrong = parts[0].trim();
                            String correct = parts[1].trim();
                            if (!wrong.isEmpty() && !correct.isEmpty()) {
                                correctionMap.put(wrong, correct);
                            }
                        } else {
                            log.warn("⚠️ Invalid correction format at line {}: {}", lineNumber, line);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("❌ Failed to load corrections.csv: {}", e.getMessage(), e);
        }
        
        return correctionMap;
    }

    @Override
    public String applyCorrections(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String correctedText = text;
        int appliedCorrections = 0;
        
        // Appliquer chaque correction dans l'ordre du CSV
        for (Map.Entry<String, String> entry : corrections.entrySet()) {
            String wrong = entry.getKey();
            String correct = entry.getValue();
            
            // Remplacer toutes les occurrences (case-sensitive)
            if (correctedText.contains(wrong)) {
                correctedText = correctedText.replace(wrong, correct);
                appliedCorrections++;
                log.trace("Applied correction: '{}' → '{}'", wrong, correct);
            }
        }
        
        if (appliedCorrections > 0) {
            log.debug("✅ Applied {} corrections to OCR text", appliedCorrections);
        }
        
        return correctedText;
    }
}
