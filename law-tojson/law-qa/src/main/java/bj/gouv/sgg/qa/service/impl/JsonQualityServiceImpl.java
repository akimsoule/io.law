package bj.gouv.sgg.qa.service.impl;

import bj.gouv.sgg.qa.service.JsonQualityService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Implémentation du service de validation qualité JSON.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JsonQualityServiceImpl implements JsonQualityService {
    
    private static final String METADATA_KEY = "_metadata";
    private static final String ARTICLES_KEY = "articles";
    private static final String SIGNATORIES_KEY = "signatories";
    
    private final Gson gson;
    
    @Override
    public boolean validateStructure(String jsonContent) {
        try {
            JsonObject root = gson.fromJson(jsonContent, JsonObject.class);
            
            // Vérifier présence des sections obligatoires
            boolean hasMetadata = root.has(METADATA_KEY);
            boolean hasArticles = root.has(ARTICLES_KEY);
            
            // Au minimum, il faut metadata et articles
            if (!hasMetadata || !hasArticles) {
                log.warn("⚠️ JSON structure invalid: metadata={}, articles={}", hasMetadata, hasArticles);
                return false;
            }
            
            // Vérifier que articles est un array non vide
            JsonArray articles = root.getAsJsonArray(ARTICLES_KEY);
            if (articles.isEmpty()) {
                log.warn("⚠️ JSON has no articles");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("❌ JSON parsing failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public double validateMetadata(JsonObject metadata) {
        if (metadata == null) {
            return 0.0;
        }
        
        int score = 0;
        int maxScore = 10;
        
        // Champs importants pour les métadonnées d'extraction
        if (hasField(metadata, "confidence")) score++;
        if (hasField(metadata, "source")) score++;
        if (hasField(metadata, "timestamp")) score++;
        if (hasField(metadata, "documentId")) score++;
        if (hasField(metadata, "type")) score++;
        if (hasField(metadata, "year")) score++;
        if (hasField(metadata, "number")) score++;
        if (hasField(metadata, "promulgationDate")) score++;
        if (hasField(metadata, "promulgationCity")) score++;
        if (hasField(metadata, ARTICLES_KEY)) score++;
        
        return (double) score / maxScore;
    }
    
    private boolean hasField(JsonObject obj, String fieldName) {
        return obj.has(fieldName) && !obj.get(fieldName).isJsonNull();
    }
    
    @Override
    public boolean validateArticleIndices(List<Integer> articleIndices) {
        if (articleIndices == null || articleIndices.isEmpty()) {
            return false;
        }
        
        // Vérifier séquentialité
        for (int i = 1; i < articleIndices.size(); i++) {
            int prev = articleIndices.get(i - 1);
            int curr = articleIndices.get(i);
            
            if (curr != prev + 1) {
                log.debug("❌ Sequence break: article {} followed by {}", prev, curr);
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public double calculateJsonQualityScore(String jsonContent) {
        try {
            JsonObject root = gson.fromJson(jsonContent, JsonObject.class);
            
            double structureScore = validateStructure(jsonContent) ? 1.0 : 0.0;
            
            // Score metadata
            double metadataScore = 0.0;
            if (root.has(METADATA_KEY)) {
                JsonObject metadata = root.getAsJsonObject(METADATA_KEY);
                metadataScore = validateMetadata(metadata);
            }
            
            // Score articles
            double articlesScore = 0.0;
            if (root.has(ARTICLES_KEY)) {
                JsonArray articles = root.getAsJsonArray(ARTICLES_KEY);
                List<Integer> indices = StreamSupport.stream(articles.spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .filter(obj -> obj.has("index"))
                    .map(obj -> obj.get("index").getAsInt())
                    .toList();
                
                articlesScore = validateArticleIndices(indices) ? 1.0 : 0.5;
            }
            
            // Score signataires (optionnel)
            double signatoriesScore = 0.0;
            if (root.has(SIGNATORIES_KEY)) {
                JsonArray signatories = root.getAsJsonArray(SIGNATORIES_KEY);
                signatoriesScore = !signatories.isEmpty() ? 1.0 : 0.0;
            }
            
            // Pondération: structure (30%), metadata (30%), articles (30%), signataires (10%)
            double totalScore = (structureScore * 0.30) + (metadataScore * 0.30) + 
                               (articlesScore * 0.30) + (signatoriesScore * 0.10);
            
            log.debug("JSON quality: structure={}, metadata={}, articles={}, signatories={} → total={}",
                     structureScore, metadataScore, articlesScore, signatoriesScore, totalScore);
            
            return totalScore;
            
        } catch (Exception e) {
            log.error("❌ Failed to calculate JSON quality: {}", e.getMessage());
            return 0.0;
        }
    }
}
