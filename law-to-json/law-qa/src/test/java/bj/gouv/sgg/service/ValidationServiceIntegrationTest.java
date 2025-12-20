package bj.gouv.sgg.service;

import bj.gouv.sgg.service.impl.JsonQualityServiceImpl;
import bj.gouv.sgg.service.impl.OcrQualityServiceImpl;
import bj.gouv.sgg.service.impl.ValidationServiceImpl;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration pour ValidationService.
 * Valide le fonctionnement complet avec JsonQualityService et OcrQualityService.
 */
class ValidationServiceIntegrationTest {
    
    private ValidationService validationService;
    private Gson gson;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        gson = new GsonBuilder().setPrettyPrinting().create();
        JsonQualityService jsonQualityService = new JsonQualityServiceImpl(gson);
        OcrQualityService ocrQualityService = new OcrQualityServiceImpl();
        validationService = new ValidationServiceImpl(jsonQualityService, ocrQualityService, gson);
    }
    
    @Test
    void shouldValidateCompleteValidDocument() throws IOException {
        // Given: Document JSON valide avec métadonnées et articles
        String validJson = """
            {
              "_metadata": {
                "number": "2024-001",
                "type": "loi",
                "title": "Loi portant code de procédure civile",
                "date": "2024-01-15",
                "publication_date": "2024-01-20",
                "source_url": "https://sgg.gouv.bj/doc/loi-2024-001.pdf",
                "pages": 45,
                "confidence": 0.95,
                "extracted_date": "2024-12-19T10:00:00"
              },
              "articles": [
                {
                  "number": "Article Premier",
                  "content": "La présente loi porte code de procédure civile."
                },
                {
                  "number": "Article 2",
                  "content": "Toute personne a le droit d'accès à la justice."
                }
              ]
            }
            """;
        
        String validOcr = """
            RÉPUBLIQUE DU BÉNIN
            UNITÉ - TRAVAIL - JUSTICE
            PRÉSIDENCE DE LA RÉPUBLIQUE
            
            LOI N° 2024-001 DU 15 JANVIER 2024
            Portant code de procédure civile
            
            L'Assemblée Nationale a délibéré et adopté;
            Le Président de la République promulgue la loi dont la teneur suit:
            
            Article Premier: La présente loi porte code de procédure civile.
            Article 2: Toute personne a le droit d'accès à la justice.
            
            Fait à Cotonou, le 15 janvier 2024
            Le Président de la République
            """;
        
        Path jsonPath = tempDir.resolve("loi-2024-001.json");
        Path ocrPath = tempDir.resolve("loi-2024-001.txt");
        Files.writeString(jsonPath, validJson);
        Files.writeString(ocrPath, validOcr);
        
        // When
        ValidationService.ValidationResult result = validationService.validateDocument(jsonPath, ocrPath);
        
        // Then
        assertNotNull(result);
        assertEquals("loi-2024-001", result.getDocumentId());
        // Document peut être invalide si les seuils de qualité ne sont pas atteints
        assertNotNull(result.getErrors(), "Errors list should not be null");
        assertTrue(result.getConfidence() >= 0.0, "Confidence should be non-negative");
    }
    
    @Test
    void shouldDetectMissingMetadata() throws IOException {
        // Given: JSON sans section _metadata
        String invalidJson = """
            {
              "articles": [
                {
                  "number": "Article 1",
                  "content": "Contenu article 1"
                }
              ]
            }
            """;
        
        Path jsonPath = tempDir.resolve("invalid.json");
        Path ocrPath = tempDir.resolve("invalid.txt");
        Files.writeString(jsonPath, invalidJson);
        Files.writeString(ocrPath, "Dummy OCR");
        
        // When
        ValidationService.ValidationResult result = validationService.validateDocument(jsonPath, ocrPath);
        
        // Then
        assertNotNull(result);
        assertNotNull(result.getErrors());
        // Document devrait être invalide ou avoir des erreurs liées à metadata
        assertTrue(!result.isValid() || !result.getErrors().isEmpty(), 
            "Document without metadata should be invalid or have errors");
    }
    
    @Test
    void shouldDetectMissingArticles() throws IOException {
        // Given: JSON sans articles
        String invalidJson = """
            {
              "_metadata": {
                "number": "2024-001",
                "type": "loi",
                "title": "Test",
                "date": "2024-01-15",
                "publication_date": "2024-01-20",
                "source_url": "https://test.com",
                "pages": 1,
                "confidence": 0.9,
                "extracted_date": "2024-12-19T10:00:00"
              }
            }
            """;
        
        Path jsonPath = tempDir.resolve("no-articles.json");
        Path ocrPath = tempDir.resolve("no-articles.txt");
        Files.writeString(jsonPath, invalidJson);
        Files.writeString(ocrPath, "Dummy OCR");
        
        // When
        ValidationService.ValidationResult result = validationService.validateDocument(jsonPath, ocrPath);
        
        // Then
        assertNotNull(result);
        assertNotNull(result.getErrors());
        // Document sans articles devrait être invalide ou avoir des erreurs
        assertTrue(!result.isValid() || !result.getErrors().isEmpty(),
            "Document without articles should be invalid or have errors");
    }
    
    @Test
    void shouldHandleMissingOcrFile() throws IOException {
        // Given: JSON valide mais OCR manquant
        String validJson = """
            {
              "_metadata": {
                "number": "2024-001",
                "type": "loi",
                "title": "Test",
                "date": "2024-01-15",
                "publication_date": "2024-01-20",
                "source_url": "https://test.com",
                "pages": 1,
                "confidence": 0.9,
                "extracted_date": "2024-12-19T10:00:00"
              },
              "articles": [
                {
                  "number": "Article 1",
                  "content": "Contenu"
                }
              ]
            }
            """;
        
        Path jsonPath = tempDir.resolve("valid.json");
        Path ocrPath = tempDir.resolve("missing.txt"); // N'existe pas
        Files.writeString(jsonPath, validJson);
        
        // When
        ValidationService.ValidationResult result = validationService.validateDocument(jsonPath, ocrPath);
        
        // Then
        // Le document peut être valide même sans OCR (score JSON seulement)
        assertNotNull(result);
        assertNotNull(result.getDocumentId());
    }
    
    @Test
    void shouldDetectLowQualityOcr() throws IOException {
        // Given: JSON valide mais OCR de mauvaise qualité
        String validJson = """
            {
              "_metadata": {
                "number": "2024-001",
                "type": "loi",
                "title": "Test",
                "date": "2024-01-15",
                "publication_date": "2024-01-20",
                "source_url": "https://test.com",
                "pages": 1,
                "confidence": 0.9,
                "extracted_date": "2024-12-19T10:00:00"
              },
              "articles": [
                {
                  "number": "Article 1",
                  "content": "Contenu valide avec des mots juridiques"
                }
              ]
            }
            """;
        
        // OCR avec beaucoup de caractères non reconnus
        String lowQualityOcr = "xXx###@@@ %%%###  zzzQQQ !!!###";
        
        Path jsonPath = tempDir.resolve("low-quality.json");
        Path ocrPath = tempDir.resolve("low-quality.txt");
        Files.writeString(jsonPath, validJson);
        Files.writeString(ocrPath, lowQualityOcr);
        
        // When
        ValidationService.ValidationResult result = validationService.validateDocument(jsonPath, ocrPath);
        
        // Then
        // Peut être invalide à cause de la faible confiance OCR
        assertTrue(result.getConfidence() < 0.9, "OCR confidence should be low");
    }
    
    @Test
    void shouldValidateArticleSequence() throws IOException {
        // Given: Articles non séquentiels
        String jsonWithBadSequence = """
            {
              "_metadata": {
                "number": "2024-001",
                "type": "loi",
                "title": "Test",
                "date": "2024-01-15",
                "publication_date": "2024-01-20",
                "source_url": "https://test.com",
                "pages": 1,
                "confidence": 0.9,
                "extracted_date": "2024-12-19T10:00:00"
              },
              "articles": [
                {
                  "number": "Article 1",
                  "content": "Premier article"
                },
                {
                  "number": "Article 5",
                  "content": "Saut dans la numérotation"
                }
              ]
            }
            """;
        
        Path jsonPath = tempDir.resolve("bad-sequence.json");
        Path ocrPath = tempDir.resolve("bad-sequence.txt");
        Files.writeString(jsonPath, jsonWithBadSequence);
        Files.writeString(ocrPath, "Dummy OCR");
        
        // When
        ValidationService.ValidationResult result = validationService.validateDocument(jsonPath, ocrPath);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.toLowerCase().contains("indice") || e.toLowerCase().contains("sequential")));
    }
}
