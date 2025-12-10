package bj.gouv.sgg.impl;

import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.model.Article;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour la validation de la qualité de séquence des articles extraits.
 * Vérifie que les pénalités sont correctement appliquées pour :
 * - Gaps (articles manquants)
 * - Duplicates (même index répété)
 * - Out-of-order (séquence décroissante)
 */
class SequenceScoreTest {

    private ArticleRegexExtractor extractor;

    @BeforeEach
    void setUp() {
        // Config minimal pour créer l'extracteur
        ArticleExtractorConfig config = new ArticleExtractorConfig();
        bj.gouv.sgg.service.UnrecognizedWordsService unrecognizedWordsService = 
            new bj.gouv.sgg.service.UnrecognizedWordsService();
        extractor = new ArticleRegexExtractor(config, unrecognizedWordsService);
    }

    /**
     * Séquence parfaite 1→2→3→4 : score = 1.0
     */
    @Test
    void testPerfectSequence() throws Exception {
        List<Article> articles = Arrays.asList(
            article(1, "Content 1"),
            article(2, "Content 2"),
            article(3, "Content 3"),
            article(4, "Content 4")
        );

        // Utilise reflection pour appeler calculateSequenceScore (méthode private)
        double score = invokeCalculateSequenceScore(articles);
        
        assertEquals(1.0, score, 0.001, "Séquence parfaite devrait avoir score 1.0");
    }

    /**
     * Articles 1→3→5 : 2 gaps (2 et 4 manquants)
     * Pénalité : 2 * 15% = 30%
     * Score attendu : 0.70
     */
    @Test
    void testSequenceWithGaps() throws Exception {
        List<Article> articles = Arrays.asList(
            article(1, "Content 1"),
            article(3, "Content 3"),  // Gap: article 2 manquant
            article(5, "Content 5")   // Gap: article 4 manquant
        );

        double score = invokeCalculateSequenceScore(articles);
        
        assertEquals(0.70, score, 0.001, "2 gaps devraient donner score 0.70 (pénalité 30%)");
    }

    /**
     * Articles 1→2→2→3 : 1 duplicate (index 2 répété)
     * Pénalité : 1 * 25% = 25%
     * Score attendu : 0.75
     */
    @Test
    void testSequenceWithDuplicates() throws Exception {
        List<Article> articles = Arrays.asList(
            article(1, "Content 1"),
            article(2, "Content 2a"),
            article(2, "Content 2b"),  // Duplicate
            article(3, "Content 3")
        );

        double score = invokeCalculateSequenceScore(articles);
        
        assertEquals(0.75, score, 0.001, "1 duplicate devrait donner score 0.75 (pénalité 25%)");
    }

    /**
     * Articles 3→2→1 : 2 out-of-order (séquence décroissante)
     * Pénalité : 2 * 30% = 60%
     * Score attendu : 0.40
     */
    @Test
    void testSequenceOutOfOrder() throws Exception {
        List<Article> articles = Arrays.asList(
            article(3, "Content 3"),
            article(2, "Content 2"),  // Out of order
            article(1, "Content 1")   // Out of order
        );

        double score = invokeCalculateSequenceScore(articles);
        
        assertEquals(0.40, score, 0.001, "2 out-of-order devraient donner score 0.40 (pénalité 60%)");
    }

    /**
     * Articles 1→3→3→2 : 1 gap + 1 duplicate + 1 out-of-order
     * Pénalité totale : 15% + 25% + 30% = 70%
     * Score attendu : 0.30
     */
    @Test
    void testSequenceWithMultipleIssues() throws Exception {
        List<Article> articles = Arrays.asList(
            article(1, "Content 1"),
            article(3, "Content 3a"), // Gap: article 2 manquant (15%)
            article(3, "Content 3b"), // Duplicate (25%)
            article(2, "Content 2")   // Out of order (30%)
        );

        double score = invokeCalculateSequenceScore(articles);
        
        assertEquals(0.30, score, 0.001, "Pénalité totale 70% → score 0.30");
    }

    /**
     * Articles 1→10 : gap énorme (articles 2-9 manquants)
     * Pénalité : 8 * 15% = 120% → score minimum 0.0
     */
    @Test
    void testSequenceWithHugeGap() throws Exception {
        List<Article> articles = Arrays.asList(
            article(1, "Content 1"),
            article(10, "Content 10")  // Gap de 8 articles
        );

        double score = invokeCalculateSequenceScore(articles);
        
        assertEquals(0.0, score, 0.001, "Pénalité > 100% → score minimum 0.0");
    }

    /**
     * Article unique (index 1) : score = 1.0
     */
    @Test
    void testSingleArticleStartingAt1() throws Exception {
        List<Article> articles = Arrays.asList(
            article(1, "Content 1")
        );

        double score = invokeCalculateSequenceScore(articles);
        
        assertEquals(1.0, score, 0.001, "Article unique à index 1 → score 1.0");
    }

    /**
     * Article unique (index 5) : pénalité car ne commence pas à 1
     * Score attendu : 0.8
     */
    @Test
    void testSingleArticleNotStartingAt1() throws Exception {
        List<Article> articles = Arrays.asList(
            article(5, "Content 5")
        );

        double score = invokeCalculateSequenceScore(articles);
        
        assertEquals(0.8, score, 0.001, "Article unique ne commençant pas à 1 → score 0.8");
    }

    // ==================== HELPERS ====================

    private Article article(int index, String content) {
        return Article.builder()
            .index(index)
            .content(content)
            .build();
    }

    /**
     * Appelle la méthode private calculateSequenceScore via reflection
     */
    private double invokeCalculateSequenceScore(List<Article> articles) throws Exception {
        var method = ArticleRegexExtractor.class.getDeclaredMethod("calculateSequenceScore", List.class);
        method.setAccessible(true);
        return (double) method.invoke(extractor, articles);
    }
}
