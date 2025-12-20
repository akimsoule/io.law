package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.service.JsonQualityService;
import bj.gouv.sgg.service.OcrQualityService;
import bj.gouv.sgg.service.ValidationService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implémentation du service de validation qualité (POJO simple).
 * 
 * @author io.law
 * @since 2.0.0
 */
@Slf4j
public class ValidationServiceImpl implements ValidationService {
    
    private final JsonQualityService jsonQualityService;
    private final OcrQualityService ocrQualityService;
    private final Gson gson;
    
    public ValidationServiceImpl(JsonQualityService jsonQualityService, 
                                  OcrQualityService ocrQualityService,
                                  Gson gson) {
        this.jsonQualityService = jsonQualityService;
        this.ocrQualityService = ocrQualityService;
        this.gson = gson;
    }
    
    @Override
    public ValidationResult validateDocument(Path jsonPath, Path ocrPath) {
        String documentId = jsonPath.getFileName().toString().replace(".json", "");
        ValidationResultImpl result = new ValidationResultImpl(documentId);
        
        try {
            // Lire JSON
            String jsonContent = Files.readString(jsonPath);
            
            // 1. Validation structure JSON
            boolean structureValid = jsonQualityService.validateStructure(jsonContent);
            if (!structureValid) {
                result.addError("Invalid JSON structure");
                log.warn("⚠️ Invalid structure: {}", documentId);
                return result;
            }
            
            JsonObject jsonObject = gson.fromJson(jsonContent, JsonObject.class);
            
            // 2. Validation métadonnées
            double metadataScore = 0.0;
            if (jsonObject.has("_metadata")) {
                JsonObject metadata = jsonObject.getAsJsonObject("_metadata");
                metadataScore = jsonQualityService.validateMetadata(metadata);
                
                if (metadataScore < 0.5) {
                    result.addError(String.format("Low metadata score: %.2f", metadataScore));
                    log.warn("⚠️ Low metadata score for {}: {:.2f}", documentId, metadataScore);
                }
            } else {
                result.addError("Missing _metadata section");
            }
            
            // 3. Validation articles
            if (jsonObject.has("articles")) {
                JsonArray articlesArray = jsonObject.getAsJsonArray("articles");
                
                // Vérifier indices d'articles
                List<Integer> indices = new ArrayList<>();
                for (int i = 0; i < articlesArray.size(); i++) {
                    JsonObject article = articlesArray.get(i).getAsJsonObject();
                    if (article.has("number")) {
                        try {
                            String numStr = article.get("number").getAsString()
                                .replaceAll("[^0-9]", ""); // Extraire chiffres
                            if (!numStr.isEmpty()) {
                                indices.add(Integer.parseInt(numStr));
                            }
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }
                }
                
                if (!indices.isEmpty()) {
                    boolean indicesValid = jsonQualityService.validateArticleIndices(indices);
                    if (!indicesValid) {
                        result.addError("Article indices not sequential");
                        log.warn("⚠️ Non-sequential articles for {}", documentId);
                    }
                }
            } else {
                result.addError("Missing articles section");
            }
            
            // 4. Calcul qualité JSON globale
            double jsonQuality = jsonQualityService.calculateJsonQualityScore(jsonContent);
            
            // 5. Validation qualité OCR (si fichier existe)
            double ocrConfidence = 0.0;
            if (Files.exists(ocrPath)) {
                String ocrText = Files.readString(ocrPath);
                
                // Parser articles depuis JSON pour calcul confiance OCR
                List<Article> articles = parseArticles(jsonObject);
                
                ocrConfidence = ocrQualityService.calculateConfidence(ocrText, articles);
                
                if (ocrConfidence < 0.5) {
                    result.addError(String.format("Low OCR confidence: %.2f", ocrConfidence));
                    log.warn("⚠️ Low OCR confidence for {}: {:.2f}", documentId, ocrConfidence);
                }
            }
            
            // Score final combiné
            double finalConfidence = (jsonQuality + ocrConfidence) / 2.0;
            result.setConfidence(finalConfidence);
            result.setValid(result.getErrors().isEmpty() && finalConfidence >= 0.5);
            
        } catch (IOException e) {
            result.addError("Failed to read file: " + e.getMessage());
            log.error("❌ Validation error for {}: {}", documentId, e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Parse articles depuis JSON object.
     */
    private List<Article> parseArticles(JsonObject jsonObject) {
        List<Article> articles = new ArrayList<>();
        
        if (jsonObject.has("articles")) {
            JsonArray articlesArray = jsonObject.getAsJsonArray("articles");
            for (int i = 0; i < articlesArray.size(); i++) {
                JsonObject articleJson = articlesArray.get(i).getAsJsonObject();
                
                Article article = Article.builder()
                    .index(i + 1)
                    .content(articleJson.has("content") ? 
                        articleJson.get("content").getAsString() : "")
                    .build();
                
                articles.add(article);
            }
        }
        
        return articles;
    }
    
    /**
     * Implémentation DTO résultat de validation.
     */
    private static class ValidationResultImpl implements ValidationResult {
        private final String documentId;
        private boolean valid = true;
        private double confidence = 0.0;
        private final List<String> errors = new ArrayList<>();
        
        public ValidationResultImpl(String documentId) {
            this.documentId = documentId;
        }
        
        @Override
        public String getDocumentId() { return documentId; }
        
        @Override
        public boolean isValid() { return valid; }
        
        public void setValid(boolean valid) { this.valid = valid; }
        
        @Override
        public double getConfidence() { return confidence; }
        
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        @Override
        public List<String> getErrors() { return Collections.unmodifiableList(errors); }
        
        public void addError(String error) { 
            this.errors.add(error);
            this.valid = false;
        }
    }
}
