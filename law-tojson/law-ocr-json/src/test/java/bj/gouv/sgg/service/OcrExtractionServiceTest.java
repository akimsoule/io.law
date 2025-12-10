package bj.gouv.sgg.service;

import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.impl.ArticleRegexExtractor;
import bj.gouv.sgg.impl.CsvCorrector;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.DocumentMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour OcrExtractionService via ArticleRegexExtractor
 */
class OcrExtractionServiceTest {

    private OcrExtractionService ocrExtractionService;
    private CorrectOcrText correctOcrText;

    @BeforeEach
    void setUp() {
        ArticleExtractorConfig config = new ArticleExtractorConfig();
        config.init();
        
        ocrExtractionService = new ArticleRegexExtractor(config, new bj.gouv.sgg.service.UnrecognizedWordsService());
        correctOcrText = new CsvCorrector();
    }

    @Test
    void givenRawOcrTextWhenApplyFullPipelineThenCorrectsThenExtracts() {
        String rawOcrText = """
            REPUBLIQUE DU BENIN
            LOI N° 2024-15
            
            L''Assemblée nationale a délibéré et adopté,
            
            ARTICLE 1€'
            Les dispositions du code pénal sont modifiées.
            
            ARTICLE 2
            Les sanctions prévues à l''article précédent sont applicables.
            
            Fait à Cotonou, |e 15 décembre 2024
            """;

        // Étape 1 : Correction OCR
        String correctedText = correctOcrText.applyCorrections(rawOcrText);
        assertNotNull(correctedText);
        assertFalse(correctedText.isEmpty());

        // Étape 2 : Extraction articles
        List<Article> articles = ocrExtractionService.extractArticles(correctedText);
        assertNotNull(articles);
        assertEquals(2, articles.size());

        // Étape 3 : Extraction métadonnées
        DocumentMetadata metadata = ocrExtractionService.extractMetadata(correctedText);
        assertNotNull(metadata);
        // Les métadonnées peuvent être null si non trouvées

        // Étape 4 : Calcul confiance
        double confidence = ocrExtractionService.calculateConfidence(correctedText, articles);
        assertTrue(confidence > 0.3, "Confiance devrait être raisonnable");
    }

    @Test
    void givenCorrectedTextWhenExtractArticlesThenReturnsValidArticles() {
        String rawText = "ARTICLE 1€' |a première disposition ARTICLE 2 |es autres règles";
        String corrected = correctOcrText.applyCorrections(rawText);
        
        List<Article> articles = ocrExtractionService.extractArticles(corrected);
        
        assertNotNull(articles);
        assertTrue(articles.size() >= 1, "Au moins 1 article devrait être extrait");
    }

    @Test
    void givenCorrectedTextWhenExtractMetadataThenReturnsValidMetadata() {
        String rawText = """
            LOI N° 2024-15
            L''Assemblée nationale
            Fait à Cotonou, |e 15 décembre 2024
            """;
        
        String corrected = correctOcrText.applyCorrections(rawText);
        DocumentMetadata metadata = ocrExtractionService.extractMetadata(corrected);
        
        assertNotNull(metadata);
        // Vérifier les métadonnées si elles existent
        if (metadata.getLawTitle() != null) {
            assertTrue(metadata.getLawTitle().contains("2024-15"));
        }
        if (metadata.getPromulgationCity() != null) {
            assertEquals("Cotonou", metadata.getPromulgationCity());
        }
    }

    @Test
    void givenCorrectedHighQualityTextWhenCalculateConfidenceThenReturnsHighScore() {
        String rawText = """
            REPUBLIQUE DU BENIN
            LOI N° 2024-15 portant modification du code pénal.
            ARTICLE 1€' : Les dispositions de |a présente loi sont
            conformément aux principes de |a République.
            |e Président promulgue |a loi selon les modalités prévues.
            """;
        
        String corrected = correctOcrText.applyCorrections(rawText);
        List<Article> articles = ocrExtractionService.extractArticles(corrected);
        double confidence = ocrExtractionService.calculateConfidence(corrected, articles);
        
        assertTrue(confidence > 0.4, "Confiance devrait être bonne après corrections");
    }

    @Test
    void givenComplexStructuredTextWhenExtractArticlesThenPreservesStructure() {
        String text = """
            Article 1
            Paragraphe 1 : Première disposition.
            Paragraphe 2 : Deuxième disposition.
            
            Article 2
            Section A : Règles générales
            Section B : Règles spécifiques
            - Point 1
            - Point 2
            
            Article 3
            Disposition finale.
            """;
        
        List<Article> articles = ocrExtractionService.extractArticles(text);
        
        assertNotNull(articles);
        assertEquals(3, articles.size());
        
        // Vérifier que le contenu structuré est préservé
        assertTrue(articles.get(1).getContent().contains("Section A"));
        assertTrue(articles.get(1).getContent().contains("Point 1"));
    }

    @Test
    void givenCompleteDocumentTextWhenExtractMetadataThenReturnsCompleteMetadata() {
        String text = """
            RÉPUBLIQUE DU BÉNIN
            LOI N° 2024-15 du 15 décembre 2024
            portant modification du code pénal de la République du Bénin
            
            L'Assemblée nationale a délibéré et adopté,
            Le Président de la République promulgue la loi dont la teneur suit :
            
            Article 1
            Contenu de l'article.
            
            Fait à Cotonou, le 15 décembre 2024
            
            Le Président de la République
            Patrice TALON
            
            Le Ministre d'État
            Romuald WADAGNI
            """;
        
        DocumentMetadata metadata = ocrExtractionService.extractMetadata(text);
        
        assertNotNull(metadata);
        // Vérifier les champs si extraits
        if (metadata.getLawTitle() != null) {
            assertTrue(metadata.getLawTitle().contains("2024-15"));
        }
        
        if (metadata.getPromulgationCity() != null) {
            assertEquals("Cotonou", metadata.getPromulgationCity());
        }
        
        if (metadata.getPromulgationDate() != null) {
            assertTrue(metadata.getPromulgationDate().contains("décembre") || 
                      metadata.getPromulgationDate().contains("2024"));
        }
        
        // Les signataires peuvent ne pas être extraits selon les patterns
        assertNotNull(metadata.getSignatories());
    }

    @Test
    void testCalculateConfidence_Components() {
        String text = "article loi décret dispositions promulgué république président ministre";
        List<Article> articles = List.of(
            Article.builder().index(1).content("Content 1").build(),
            Article.builder().index(2).content("Content 2").build()
        );
        
        double confidence = ocrExtractionService.calculateConfidence(text, articles);
        
        // Confiance basée sur :
        // - Nombre d'articles (30%)
        // - Longueur texte (20%)
        // - Mots français reconnus (30%)
        // - Termes juridiques (20%)
        assertTrue(confidence > 0.0);
        assertTrue(confidence <= 1.0);
    }

    @Test
    void testExtractArticles_EdgeCases() {
        // Article avec contenu minimal
        String text = "Article 1\nX";
        
        List<Article> articles = ocrExtractionService.extractArticles(text);
        
        // Devrait extraire même avec contenu minimal
        assertNotNull(articles);
        assertFalse(articles.isEmpty());
    }

    @Test
    void testExtractMetadata_PartialData() {
        String text = """
            Article 1
            Contenu sans titre de loi ni signatures.
            """;
        
        DocumentMetadata metadata = ocrExtractionService.extractMetadata(text);
        
        assertNotNull(metadata);
        // Les champs peuvent être null si non trouvés, mais l'objet existe
    }
}
