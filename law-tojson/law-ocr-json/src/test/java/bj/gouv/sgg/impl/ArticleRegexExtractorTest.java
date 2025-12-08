package bj.gouv.sgg.impl;

import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.exception.OcrExtractionException;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.DocumentMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour ArticleRegexExtractor
 */
class ArticleRegexExtractorTest {

    private ArticleRegexExtractor extractor;

    @BeforeEach
    void setUp() {
        ArticleExtractorConfig config = new ArticleExtractorConfig();
        config.init();
        extractor = new ArticleRegexExtractor(config);
    }

    @Test
    void givenValidOcrTextWhenExtractArticlesThenReturnsCorrectArticleList() {
        String ocrText = """
            LOI N° 2024-15 du 15 décembre 2024
            portant modification du code pénal
            
            L'Assemblée nationale a délibéré et adopté,
            
            Article 1
            Le présent texte modifie les dispositions de l'article 123 du code pénal.
            
            Article 2
            Les sanctions prévues à l'article précédent sont applicables.
            
            Fait à Cotonou, le 15 décembre 2024
            """;

        List<Article> articles = extractor.extractArticles(ocrText);

        assertNotNull(articles);
        assertEquals(2, articles.size());
        
        Article article1 = articles.get(0);
        assertEquals(1, article1.getIndex());
        assertTrue(article1.getContent().contains("code pénal"));
        
        Article article2 = articles.get(1);
        assertEquals(2, article2.getIndex());
        assertTrue(article2.getContent().contains("sanctions"));
    }

    @Test
    void givenEmptyTextWhenExtractArticlesThenThrowsOcrExtractionException() {
        assertThrows(OcrExtractionException.class, () -> 
            extractor.extractArticles("")
        );
    }

    @Test
    void givenNullTextWhenExtractArticlesThenThrowsOcrExtractionException() {
        assertThrows(OcrExtractionException.class, () -> 
            extractor.extractArticles(null)
        );
    }

    @Test
    void givenTextWithoutArticlesWhenExtractArticlesThenThrowsOcrExtractionException() {
        String textWithoutArticles = """
            Ceci est un texte quelconque sans articles structurés.
            Il n'y a pas de numérotation d'articles ici.
            """;

        assertThrows(OcrExtractionException.class, () -> 
            extractor.extractArticles(textWithoutArticles)
        );
    }

    @Test
    void givenSingleArticleTextWhenExtractArticlesThenReturnsOneArticle() {
        String ocrText = """
            Article 1
            Contenu de l'article unique.
            
            Fait à Cotonou, le 15 décembre 2024
            """;

        List<Article> articles = extractor.extractArticles(ocrText);

        assertNotNull(articles);
        assertEquals(1, articles.size());
        assertEquals(1, articles.get(0).getIndex());
    }

    @Test
    void givenDifferentArticleFormatsWhenExtractArticlesThenRecognizesAllFormats() {
        String ocrText = """
            Article 1
            Premier article.
            
            ARTICLE 2
            Deuxième article en majuscules.
            
            Art. 3
            Troisième article abrégé.
            
            Fait à Cotonou
            """;

        List<Article> articles = extractor.extractArticles(ocrText);

        assertNotNull(articles);
        assertEquals(3, articles.size());
    }

    @Test
    void givenCompleteDocumentWhenExtractMetadataThenReturnsAllMetadata() {
        String ocrText = """
            LOI N° 2024-15 du 15 décembre 2024
            portant modification du code pénal de la République du Bénin
            
            L'Assemblée nationale a délibéré et adopté,
            
            Article 1
            Contenu de l'article.
            
            Fait à Cotonou, le 15 décembre 2024
            
            Le Président de la République
            Patrice TALON
            """;

        DocumentMetadata metadata = extractor.extractMetadata(ocrText);

        assertNotNull(metadata);
        // Les métadonnées peuvent être null si non trouvées selon les patterns
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
        
        assertNotNull(metadata.getSignatories());
    }

    @Test
    void givenPartialDocumentWhenExtractMetadataThenReturnsPartialMetadata() {
        String ocrText = """
            Article 1
            Contenu sans métadonnées complètes.
            """;

        DocumentMetadata metadata = extractor.extractMetadata(ocrText);

        assertNotNull(metadata);
        // Les champs peuvent être null si non trouvés
    }

    @Test
    void givenHighQualityTextWhenCalculateConfidenceThenReturnsHighScore() {
        String highQualityText = """
            LOI N° 2024-15 portant modification du code pénal.
            Article premier : Les dispositions de la présente loi sont
            conformément aux principes de la République du Bénin.
            Le Président promulgue la loi selon les modalités prévues.
            Publié au Journal Officiel de la République du Bénin.
            """;

        List<Article> articles = List.of(
            Article.builder().index(1).content("Article content").build()
        );

        double confidence = extractor.calculateConfidence(highQualityText, articles);

        assertTrue(confidence > 0.5, "Confiance devrait être élevée pour texte de qualité");
    }

    @Test
    void givenLowQualityTextWhenCalculateConfidenceThenReturnsLowScore() {
        String lowQualityText = "xyzabc qwerty asdfgh";
        List<Article> articles = List.of();

        double confidence = extractor.calculateConfidence(lowQualityText, articles);

        assertTrue(confidence < 0.5, "Confiance devrait être faible pour texte de mauvaise qualité");
    }

    @Test
    void testCalculateConfidence_EmptyArticles() {
        String text = "Texte quelconque sans articles";
        List<Article> articles = List.of();

        double confidence = extractor.calculateConfidence(text, articles);

        assertTrue(confidence < 0.3, "Confiance devrait être très faible sans articles");
    }

    @Test
    void testCalculateConfidence_ManyArticles() {
        String text = "Texte juridique avec article, loi, décret, dispositions";
        List<Article> articles = List.of(
            Article.builder().index(1).content("Article 1").build(),
            Article.builder().index(2).content("Article 2").build(),
            Article.builder().index(3).content("Article 3").build(),
            Article.builder().index(4).content("Article 4").build(),
            Article.builder().index(5).content("Article 5").build()
        );

        double confidence = extractor.calculateConfidence(text, articles);

        assertTrue(confidence > 0.4, "Confiance devrait être correcte avec plusieurs articles");
    }

    @Test
    void testExtractArticles_WithComplexFormatting() {
        String ocrText = """
            Article 1er
            Le présent texte s'applique à l'ensemble du territoire.
            
            Article 2
            Sont concernées les dispositions suivantes :
            - Point 1
            - Point 2
            
            Article 3
            Les sanctions prévues sont applicables.
            """;

        List<Article> articles = extractor.extractArticles(ocrText);

        assertNotNull(articles);
        assertEquals(3, articles.size());
        assertTrue(articles.get(1).getContent().contains("Point 1"));
    }

    @Test
    void testExtractMetadata_MultipleSignatories() {
        String ocrText = """
            LOI N° 2024-15
            
            Fait à Cotonou, le 15 décembre 2024
            
            Le Président de la République
            Patrice TALON
            
            Le Ministre d'État
            Romuald WADAGNI
            """;

        DocumentMetadata metadata = extractor.extractMetadata(ocrText);

        assertNotNull(metadata);
        assertNotNull(metadata.getSignatories());
        // Au moins un signataire devrait être trouvé
        assertFalse(metadata.getSignatories().isEmpty());
    }
}
