package bj.gouv.sgg.service;

import bj.gouv.sgg.service.impl.JsonQualityServiceImpl;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour JsonQualityService.
 */
class JsonQualityServiceTest {
    
    private JsonQualityService jsonQualityService;
    private Gson gson;
    
    @BeforeEach
    void setUp() {
        gson = new Gson();
        jsonQualityService = new JsonQualityServiceImpl(gson);
    }
    
    @Test
    void shouldValidateCorrectStructure() {
        // Given
        String validJson = """
            {
              "_metadata": {
                "number": "2024-001",
                "type": "loi"
              },
              "articles": [
                {
                  "number": "Article 1",
                  "content": "Contenu"
                }
              ]
            }
            """;
        
        // When
        boolean valid = jsonQualityService.validateStructure(validJson);
        
        // Then
        assertTrue(valid, "Valid JSON structure should pass");
    }
    
    @Test
    void shouldRejectMissingMetadata() {
        // Given
        String invalidJson = """
            {
              "articles": [
                {
                  "number": "Article 1",
                  "content": "Contenu"
                }
              ]
            }
            """;
        
        // When
        boolean valid = jsonQualityService.validateStructure(invalidJson);
        
        // Then
        assertFalse(valid, "JSON without metadata should be invalid");
    }
    
    @Test
    void shouldRejectMissingArticles() {
        // Given
        String invalidJson = """
            {
              "_metadata": {
                "number": "2024-001"
              }
            }
            """;
        
        // When
        boolean valid = jsonQualityService.validateStructure(invalidJson);
        
        // Then
        assertFalse(valid, "JSON without articles should be invalid");
    }
    
    @Test
    void shouldRejectEmptyArticles() {
        // Given
        String invalidJson = """
            {
              "_metadata": {
                "number": "2024-001"
              },
              "articles": []
            }
            """;
        
        // When
        boolean valid = jsonQualityService.validateStructure(invalidJson);
        
        // Then
        assertFalse(valid, "JSON with empty articles should be invalid");
    }
    
    @Test
    void shouldValidateCompleteMetadata() {
        // Given
        JsonObject metadata = new JsonObject();
        metadata.addProperty("number", "2024-001");
        metadata.addProperty("type", "loi");
        metadata.addProperty("title", "Test");
        metadata.addProperty("date", "2024-01-15");
        metadata.addProperty("publication_date", "2024-01-20");
        metadata.addProperty("source_url", "https://test.com");
        metadata.addProperty("pages", 10);
        metadata.addProperty("confidence", 0.95);
        metadata.addProperty("extracted_date", "2024-12-19");
        
        // When
        double score = jsonQualityService.validateMetadata(metadata);
        
        // Then
        assertTrue(score >= 0.3, "Complete metadata should have decent score (got: " + score + ")");
        assertTrue(score <= 1.0, "Score should not exceed 1.0");
    }
    
    @Test
    void shouldScorePartialMetadata() {
        // Given
        JsonObject metadata = new JsonObject();
        metadata.addProperty("number", "2024-001");
        metadata.addProperty("type", "loi");
        // Manque 7 champs sur 9
        
        // When
        double score = jsonQualityService.validateMetadata(metadata);
        
        // Then
        assertTrue(score < 0.5, "Partial metadata should have low score");
        assertTrue(score > 0.0, "Partial metadata should have some score");
    }
    
    @Test
    void shouldValidateSequentialIndices() {
        // Given
        List<Integer> sequential = Arrays.asList(1, 2, 3, 4, 5);
        
        // When
        boolean valid = jsonQualityService.validateArticleIndices(sequential);
        
        // Then
        assertTrue(valid, "Sequential indices should be valid");
    }
    
    @Test
    void shouldRejectNonSequentialIndices() {
        // Given
        List<Integer> nonSequential = Arrays.asList(1, 2, 5, 6); // Saute 3 et 4
        
        // When
        boolean valid = jsonQualityService.validateArticleIndices(nonSequential);
        
        // Then
        assertFalse(valid, "Non-sequential indices should be invalid");
    }
    
    @Test
    void shouldCalculateQualityScore() {
        // Given
        String qualityJson = """
            {
              "_metadata": {
                "number": "2024-001",
                "type": "loi",
                "title": "Test complet",
                "date": "2024-01-15",
                "publication_date": "2024-01-20",
                "source_url": "https://test.com",
                "pages": 10,
                "confidence": 0.95,
                "extracted_date": "2024-12-19"
              },
              "articles": [
                {
                  "number": "Article 1",
                  "content": "Premier article avec contenu"
                },
                {
                  "number": "Article 2",
                  "content": "Deuxième article avec contenu"
                }
              ]
            }
            """;
        
        // When
        double score = jsonQualityService.calculateJsonQualityScore(qualityJson);
        
        // Then
        assertTrue(score > 0.0, "Good quality JSON should have positive score (got: " + score + ")");
        assertTrue(score <= 1.0, "Score should not exceed 1.0");
    }
}
