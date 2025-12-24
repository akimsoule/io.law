package bj.gouv.sgg.service.correction.impl;

import bj.gouv.sgg.config.ArticleExtractorConfig;
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
public class CorrectOcrTextImpl implements CorrectOcrText {

    private final LawDocumentService lawDocumentService;
    private final ErrorCorrectionRepository errorCorrectionRepository;
    private final ArticleExtractorConfig articleExtractorConfig;
    private final LinguisticCorrectionImpl linguisticCorrection;

    // Cache corrections for performance: wrong -> correction
    private final java.util.Map<String, String> correctionsCache = new java.util.LinkedHashMap<>();

    public CorrectOcrTextImpl(LawDocumentService lawDocumentService,
                              ErrorCorrectionRepository errorCorrectionRepository,
                              ArticleExtractorConfig articleExtractorConfig,
                              LinguisticCorrectionImpl linguisticCorrectionService) {
        this.lawDocumentService = lawDocumentService;
        this.errorCorrectionRepository = errorCorrectionRepository;
        this.articleExtractorConfig = articleExtractorConfig;
        this.linguisticCorrection = linguisticCorrectionService;
        long correctionSize = this.errorCorrectionRepository.count();
        log.info("✅ Loaded {} OCR corrections (database + CSV)", correctionSize);

        // Load corrections into cache for fast lookup
        try {
            java.util.List<bj.gouv.sgg.entity.ErrorCorrection> list = this.errorCorrectionRepository.findByCorrectionTextIsNotNull();
            for (bj.gouv.sgg.entity.ErrorCorrection ec : list) {
                if (ec.getErrorFound() != null && ec.getCorrectionText() != null) {
                    correctionsCache.put(ec.getErrorFound(), ec.getCorrectionText());
                }
            }
            log.info("✅ Cached {} correction entries", correctionsCache.size());
        } catch (Exception e) {
            log.warn("⚠️ Unable to load corrections into cache: {}", e.getMessage());
        }
    }

    @Override
    public String parseOCRFile(LawDocumentEntity document, File file) {
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
                Set<String> unrecognizedWords = this.articleExtractorConfig.getUnrecognizedWords(correctedLine);
                for (String word : unrecognizedWords) {
                    unrecognizedCount.put(word, unrecognizedCount.getOrDefault(word, 0) + 1);
                }
            }

            //get data
            String type = document.getType();
            String documentId = document.getDocumentId();

            LawDocumentEntity lawDocumentEntity;
            Optional<LawDocumentEntity> optionalLawDocumentEntity = lawDocumentService.findByDocumentId(documentId);

            lawDocumentEntity = optionalLawDocumentEntity.orElseGet(() -> LawDocumentEntity.createFromDocumentId(documentId, type));

            updateErrorCorrectionRepository(lawDocumentEntity, unrecognizedCount);

            // Utiliser langtool pour mettre à jour les corrections les plus évidentes
            autoCorrectWithLanguageTool();
        } catch (IOException e) {
            log.error("❌ Failed to parse OCR file: {}", e.getMessage(), e);
            return null;
        }

        log.info("✅ Parsing completed for file: {}. Unrecognized words: {}", file.getName(), unrecognizedCount.size());
        return correctedContent.toString();
    }

    @Override
    public String applyCorrections(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String correctedText = text;
        int appliedCorrections = 0;

        // Use cached corrections for performance and apply with word-boundaries (case-insensitive)
        for (java.util.Map.Entry<String, String> entry : correctionsCache.entrySet()) {
            String wrong = entry.getKey();
            String correct = entry.getValue();

            try {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(wrong) + "\\b",
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
                java.util.regex.Matcher m = p.matcher(correctedText);
                if (m.find()) {
                    correctedText = m.replaceAll(java.util.regex.Matcher.quoteReplacement(correct));
                    appliedCorrections++;
                    log.trace("Applied correction: '{}' → '{}'", wrong, correct);
                }
            } catch (Exception e) {
                log.debug("⚠️ Skipping correction '{}' due to error: {}", wrong, e.getMessage());
            }
        }

        if (appliedCorrections > 0) {
            log.info("✅ Applied {} OCR corrections to text ({} chars)", appliedCorrections, correctedText.length());
        }

        return correctedText;
    }


    private void updateErrorCorrectionRepository(LawDocumentEntity lawDocumentEntity, Map<String, Integer> unrecognizedCount) {
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
                    bj.gouv.sgg.entity.ErrorCorrection ec = new bj.gouv.sgg.entity.ErrorCorrection();
                    ec.setErrorFound(word);
                    ec.setErrorCount(count);
                    ec.setCorrectionText(null);
                    ec.setCorrectionIsAutomatic(false);
                    errorCorrectionRepository.save(ec);
                    log.info("ℹ️ New word '{}' in the database.", word);
                }
            }
        }
    }


    /**
     * Applique des règles de sécurité pour n'accepter que des corrections peu ambiguës.
     */
    /**
     * Parcourt la table {@link ErrorCorrection} et tente d'ajouter des corrections sûres
     * en utilisant {@link LinguisticCorrectionImpl#getSafeSuggestion(String)}.
     * Cette méthode remplace la logique dupliquée qui créait et configurait un
     * langtool local
     */
    private void autoCorrectWithLanguageTool() {
        try {
            // 1. On ne récupère QUE ce qui n'est pas encore corrigé
            List<ErrorCorrection> toProcess = errorCorrectionRepository.findByCorrectionTextIsNull();
            if (toProcess.isEmpty()) return;

            List<ErrorCorrection> updatedEntries = new ArrayList<>();

            for (ErrorCorrection ec : toProcess) {
                String found = ec.getErrorFound();
                if (found == null || found.isBlank()) continue;

                String suggestion = linguisticCorrection.getSafeSuggestion(found);

                if (suggestion != null && !suggestion.equalsIgnoreCase(found)) {
                    ec.setCorrectionText(suggestion);
                    ec.setCorrectionIsAutomatic(true);
                    updatedEntries.add(ec);
                    log.info("✅ Auto-corrected: '{}' → '{}'", found, suggestion);
                }
            }

            // 2. Sauvegarde groupée (beaucoup plus rapide)
            if (!updatedEntries.isEmpty()) {
                errorCorrectionRepository.saveAll(updatedEntries);
                log.info("📊 Total auto-corrections appliquées : {}", updatedEntries.size());
            }

        } catch (Exception e) {
            log.error("❌ Échec de l'auto-correction : {}", e.getMessage(), e);
        }
    }


}