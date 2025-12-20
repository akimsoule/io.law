package bj.gouv.sgg.service;

import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.service.impl.OcrQualityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour OcrQualityService.
 */
class OcrQualityServiceTest {
    
    private OcrQualityService ocrQualityService;
    
    @BeforeEach
    void setUp() {
        ocrQualityService = new OcrQualityServiceImpl();
    }
    
    @Test
    void shouldDetectJuridicalPatterns() {
        // Given: Texte avec patterns juridiques béninois
        String ocrText = """
            RÉPUBLIQUE DU BÉNIN
            UNITÉ - TRAVAIL - JUSTICE
            PRÉSIDENCE DE LA RÉPUBLIQUE
            
            LOI N° 2024-001 DU 15 JANVIER 2024
            
            L'Assemblée Nationale a délibéré et adopté;
            Le Président de la République promulgue la loi dont la teneur suit:
            
            Article Premier: Disposition générale
            Article 2: Application de la loi
            
            Fait à Cotonou, le 15 janvier 2024
            """;
        
        List<Article> articles = Arrays.asList(
            Article.builder().index(1).content("Disposition générale").build(),
            Article.builder().index(2).content("Application de la loi").build()
        );
        
        // When
        double confidence = ocrQualityService.calculateConfidence(ocrText, articles);
        
        // Then
        assertTrue(confidence > 0.5, "Text with juridical patterns should have high confidence");
    }
    
    @Test
    void shouldDetectLowQualityOcr() {
        // Given: Texte avec beaucoup de caractères non reconnus
        String poorOcrText = "xXx### @@@ %%% zzzQQQ !!!###";
        List<Article> articles = Arrays.asList(
            Article.builder().index(1).content("Test").build()
        );
        
        // When
        double confidence = ocrQualityService.calculateConfidence(poorOcrText, articles);
        
        // Then
        assertTrue(confidence < 0.5, "Poor OCR should have low confidence");
    }
    
    @Test
    void shouldDetectUnrecognizedWords() {
        // Given: Texte avec mots français et non-mots
        String mixedText = "Le tribunal prononce jugement xXxQQQ zzz###";
        
        // When
        int unrecognizedCount = ocrQualityService.detectUnrecognizedWords(mixedText, "test-001");
        
        // Then
        assertTrue(unrecognizedCount > 0, "Should detect non-French words");
    }
    
    @Test
    void shouldRecognizeFrenchWords() {
        // Given: Texte en français correct
        String frenchText = "La justice est rendue au nom du peuple";
        
        // When
        int unrecognizedCount = ocrQualityService.detectUnrecognizedWords(frenchText, "test-002");
        
        // Then
        // Tous les mots devraient être reconnus (dictionnaire français)
        assertTrue(unrecognizedCount < 3, "Common French words should be mostly recognized");
    }
    
    @Test
    void shouldCalculateOcrConfidenceWithArticles() {
        // Given: Texte OCR cohérent avec articles
        String ocrText = """
            Article Premier: La présente loi établit les règles de procédure.
            Article 2: Toute personne a droit à un procès équitable.
            Article 3: Les jugements sont rendus publiquement.
            """;
        
        List<Article> articles = Arrays.asList(
            Article.builder().index(1).content("La présente loi établit les règles de procédure").build(),
            Article.builder().index(2).content("Toute personne a droit à un procès équitable").build(),
            Article.builder().index(3).content("Les jugements sont rendus publiquement").build()
        );
        
        // When
        double confidence = ocrQualityService.calculateConfidence(ocrText, articles);
        
        // Then
        assertTrue(confidence > 0.0, "Coherent OCR with articles should have positive confidence (got: " + confidence + ")");
        assertTrue(confidence <= 1.0, "Confidence should not exceed 1.0");
    }
    
    @Test
    void shouldHandleEmptyOcr() {
        // Given
        String emptyOcr = "";
        List<Article> articles = Arrays.asList(
            Article.builder().index(1).content("Test").build()
        );
        
        // When
        double confidence = ocrQualityService.calculateConfidence(emptyOcr, articles);
        
        // Then
        assertTrue(confidence <= 0.1, "Empty OCR should have very low confidence");
    }
    
    @Test
    void shouldHandleEmptyArticles() {
        // Given
        String ocrText = "RÉPUBLIQUE DU BÉNIN\nTest document";
        List<Article> emptyArticles = List.of();
        
        // When
        double confidence = ocrQualityService.calculateConfidence(ocrText, emptyArticles);
        
        // Then
        assertTrue(confidence >= 0.0, "Should handle empty articles without error");
    }
}
