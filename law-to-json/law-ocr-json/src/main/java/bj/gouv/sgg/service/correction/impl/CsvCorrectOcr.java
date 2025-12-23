package bj.gouv.sgg.service.correction.impl;

import bj.gouv.sgg.entity.ErrorCorrection;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.OCRProcessusStatus;
import bj.gouv.sgg.repository.ErrorCorrectionRepository;
import bj.gouv.sgg.service.LawDocumentService;
import bj.gouv.sgg.service.correction.CorrectOcrText;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Implémentation des corrections OCR depuis la base de données et corrections.csv.
 */
@Slf4j
public class CsvCorrectOcr implements CorrectOcrText {

    private final LawDocumentService lawDocumentService;
    private final ErrorCorrectionRepository errorCorrectionRepository;
    private List<String> dictionaryWord;

    public CsvCorrectOcr(LawDocumentService lawDocumentService,
                         ErrorCorrectionRepository errorCorrectionRepository) {
        this.lawDocumentService = lawDocumentService;
        this.errorCorrectionRepository = errorCorrectionRepository;
        loadCorrectionCsvInDb();
        loadDictionary();
        long correctionSize = this.errorCorrectionRepository.count();
        log.info("✅ Loaded {} OCR corrections (database + CSV)", correctionSize);
    }

    @Override
    public void loadCorrectionCsvInDb() {
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
                    line = line.trim();

                    // Skip empty lines and comments
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    // Parse correction line (format: wrong,correct)
                    if (!line.contains(",")) {
                        log.warn("⚠️ Invalid correction format at line {}: {}", lineNumber, line);
                        continue;
                    }

                    String[] parts = line.split(",", 2);
                    if (parts.length != 2) {
                        log.warn("⚠️ Invalid correction format at line {}: {}", lineNumber, line);
                        continue;
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
        } catch (IOException e) {
            log.error("❌ Failed to load corrections.csv: {}", e.getMessage(), e);
        }
    }

    @Override
    public void loadDictionary() {
        dictionaryWord = new ArrayList<>();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("liste.de.mots.francais.frgut.txt")) {
            if (is == null) {
                log.warn("⚠️ liste.de.mots.francais.frgut.txt not found in resources");
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        dictionaryWord.add(line);
                    }
                }
                log.info("✅ Loaded {} words into dictionary", dictionaryWord.size());
            }
        } catch (IOException e) {
            log.error("❌ Failed to load dictionary: {}", e.getMessage(), e);
        }
    }


    public String parseOCRFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            log.warn("⚠️ Invalid file provided: {}", file);
            return null;
        }

        StringBuilder correctedContent = new StringBuilder();
        Map<String, Integer> unrecognizedCount = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), 
            StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Correct the line using the repository
                String correctedLine = applyCorrections(line);
                correctedContent.append(correctedLine).append("\n");

                // Check for unrecognized words
                String[] words = correctedLine.split("\\s+");
                for (String word : words) {
                    if (!dictionaryWord.contains(word)) {
                        unrecognizedCount.put(word, unrecognizedCount.getOrDefault(word, 0) + 1);
                    }
                }
            }

            //get id from file name
            String fileName = file.getName();
            String type = "";
            String documentId = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;

            LawDocumentEntity lawDocumentEntity;
            Optional<LawDocumentEntity> optionalLawDocumentEntity = lawDocumentService.findByDocumentId(documentId);

            lawDocumentEntity = optionalLawDocumentEntity.orElseGet(() -> LawDocumentEntity.createFromDocumentId(documentId, type));

            if (!OCRProcessusStatus.COUNT_UNKNOWN_WORD.equals(lawDocumentEntity.getOcrProcessusStatus())) {
                // Update ErrorCorrectionRepository with unrecognized words
                for (Map.Entry<String, Integer> entry : unrecognizedCount.entrySet()) {
                    String word = entry.getKey();
                    int count = entry.getValue();

                    Optional<ErrorCorrection> errorCorrectionOptional = errorCorrectionRepository.findByErrorFound(word);

                    if (errorCorrectionOptional.isPresent()) {
                        ErrorCorrection errorCorrection = errorCorrectionOptional.get();
                        errorCorrection.setErrorCount(errorCorrection.getErrorCount() + 1);
                        errorCorrectionRepository.save(errorCorrection);
                        log.info("✅ Added unrecognized word to database: '{}' (count: {})", word, count);
                        lawDocumentEntity.setOcrProcessusStatus(OCRProcessusStatus.COUNT_UNKNOWN_WORD);
                        lawDocumentService.save(lawDocumentEntity);
                        log.info("Updated lawDocumentEntity status for OCRProcessusStatus to COUNT_UNKNOWN_WORD");
                    } else {
                        errorCorrectionRepository.save(new ErrorCorrection(null, word, count, null));
                        log.info("ℹ️ Word '{}' already exists in the database, skipping.", word);
                    }
                }
            }

        } catch (IOException e) {
            log.error("❌ Failed to parse OCR file: {}", e.getMessage(), e);
            return null;
        }

        log.info("✅ Parsing completed for file: {}. Unrecognized words: {}", file.getName(), unrecognizedCount.size());
        return correctedContent.toString();
    }

    /**
     * Charge les corrections depuis la base de données et corrections.csv.
     */
    public List<ErrorCorrection> loadCorrections() {
        return errorCorrectionRepository.findByCorrectionText(null);
    }

    @Override
    public String applyCorrections(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        List<ErrorCorrection> errorCorrections = loadCorrections();

        String correctedText = text;
        int appliedCorrections = 0;

        // Appliquer chaque correction dans l'ordre
        for (ErrorCorrection errorCorrection : errorCorrections) {
            String wrong = errorCorrection.getErrorFound();
            String correct = errorCorrection.getCorrectionText();

            // Remplacer toutes les occurrences (case-sensitive)
            if (correctedText.contains(wrong)) {
                correctedText = correctedText.replace(wrong, correct);
                appliedCorrections++;
                log.trace("Applied correction: '{}' → '{}'", wrong, correct);
            }
        }

        if (appliedCorrections > 0) {
            log.info("✅ Applied {} OCR corrections to text ({} chars)", appliedCorrections, correctedText.length());
        }

        return correctedText;
    }
}