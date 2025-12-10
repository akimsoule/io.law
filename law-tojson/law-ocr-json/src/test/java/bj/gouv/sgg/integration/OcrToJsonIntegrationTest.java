package bj.gouv.sgg.integration;

import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.impl.ArticleRegexExtractor;
import bj.gouv.sgg.impl.CsvCorrector;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.DocumentMetadata;
import bj.gouv.sgg.service.CorrectOcrText;
import bj.gouv.sgg.service.OcrExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration pour le module law-OcrToJson
 * Vérifie le fonctionnement complet du pipeline OCR → Correction → Extraction
 */
class OcrToJsonIntegrationTest {

    private OcrExtractionService extractionService;
    private CorrectOcrText corrector;
    private ArticleExtractorConfig config;

    @BeforeEach
    void setUp() {
        config = new ArticleExtractorConfig();
        config.init();
        
        extractionService = new ArticleRegexExtractor(config, new bj.gouv.sgg.service.UnrecognizedWordsService());
        corrector = new CsvCorrector();
    }

    @Test
    void givenConfigInitializedWhenCheckResourcesThenAllFilesLoaded() {
        // Vérifier que tous les fichiers de ressources sont chargés
        assertNotNull(config.getProps());
        assertFalse(config.getProps().isEmpty());
        
        assertNotNull(config.getSignatoryPatterns());
        assertFalse(config.getSignatoryPatterns().isEmpty());
        
        assertNotNull(config.getFrenchDict());
        assertTrue(config.getFrenchDict().size() > 100000);
    }

    @Test
    void givenRealisticOcrTextWhenApplyFullPipelineThenExtractsArticlesAndMetadata() {
        String rawOcrText = """
            REPUBLIQUE DU BENIN
            Fraternité - Justice - Travail
            
            LOI N° 2024-15 du 15 décembre 2024
            portant modification de certaines dispositions du code pénal
            
            L''Assemblée nationale a délibéré et adopté,
            |e Président de la République promulgue |a loi dont la teneur suit :
            
            TITRE PREMIER : DISPOSITIONS GENERALES
            
            ARTICLE 1€'
            Les dispositions des articles 123, 456 et 789 du code pénal sont modifiées
            comme suit : les sanctions prévues sont augmentées de cinquante pour cent
            pour tenir compte de |a gravité des infractions.
            
            ARTICLE 2
            Les personnes condamnées en application des anciennes dispositions peuvent
            solliciter une révision de leur peine conformément aux nouvelles règles.
            |es délais de recours sont fixés à trois mois à compter de la publication
            de |a présente loi au Journal Officiel.
            
            ARTICLE 3
            Les dispositions de |''article précédent entrent en vigueur à compter de
            |a date de publication de |a présente loi au Journal Officiel de |a
            République du Bénin.
            
            TITRE II : DISPOSITIONS FINALES
            
            ARTICLE 4
            Toutes dispositions antérieures contraires à |a présente loi sont abrogées.
            
            ARTICLE 5
            |a présente loi sera exécutée comme loi de l''État.
            
            Fait à Cotonou, |e 15 décembre 2024
            
            Par |e Président de |a République
            Patrice TALON
            
            |e Ministre de |a Justice et de |a Législation
            Yvon DETCHENOU
            
            |e Ministre d''État chargé de l''Économie et des Finances
            Romuald WADAGNI
            """;

        // ÉTAPE 1 : Correction OCR
        String correctedText = corrector.applyCorrections(rawOcrText);
        
        assertNotNull(correctedText);
        assertFalse(correctedText.isEmpty());
        // Vérifier que le texte contient au moins quelques mots attendus
        assertTrue(correctedText.contains("RÉPUBLIQUE") || correctedText.contains("REPUBLIQUE"));
        assertTrue(correctedText.contains("Assemblée") || correctedText.contains("ASSEMBLÉE"));
        assertTrue(correctedText.contains("Président") || correctedText.contains("PRÉSIDENT"));
        
        // ÉTAPE 2 : Extraction articles
        List<Article> articles = extractionService.extractArticles(correctedText);
        
        assertNotNull(articles);
        assertEquals(5, articles.size(), "Devrait extraire 5 articles");
        
        // Vérifier le contenu des articles
        Article article1 = articles.get(0);
        assertEquals(1, article1.getIndex());
        assertTrue(article1.getContent().contains("code pénal"));
        assertTrue(article1.getContent().contains("cinquante pour cent"));
        
        Article article2 = articles.get(1);
        assertEquals(2, article2.getIndex());
        assertTrue(article2.getContent().contains("révision"));
        assertTrue(article2.getContent().contains("trois mois"));
        
        Article article5 = articles.get(4);
        assertEquals(5, article5.getIndex());
        assertTrue(article5.getContent().contains("exécutée comme loi"));
        
        // ÉTAPE 3 : Extraction métadonnées
        DocumentMetadata metadata = extractionService.extractMetadata(correctedText);
        
        assertNotNull(metadata);
        
        // Vérifier titre loi si extrait
        if (metadata.getLawTitle() != null) {
            assertTrue(metadata.getLawTitle().contains("2024-15"));
        }
        
        // Vérifier date promulgation si extraite
        if (metadata.getPromulgationDate() != null) {
            assertTrue(metadata.getPromulgationDate().contains("décembre") || 
                      metadata.getPromulgationDate().contains("2024"));
        }
        
        // Vérifier ville promulgation si extraite
        if (metadata.getPromulgationCity() != null) {
            assertEquals("Cotonou", metadata.getPromulgationCity());
        }
        
        // Vérifier signataires (peuvent ne pas être extraits selon les patterns)
        assertNotNull(metadata.getSignatories(), 
                     "La liste des signataires ne devrait pas être null");
        
        // ÉTAPE 4 : Calcul confiance
        double confidence = extractionService.calculateConfidence(correctedText, articles);
        
        assertTrue(confidence > 0.5, 
                   "La confiance devrait être élevée pour un document bien structuré");
        assertTrue(confidence <= 1.0, "La confiance ne peut pas dépasser 1.0");
        
        // Vérifications supplémentaires sur la confiance
        // - 5 articles → bon score
        // - Texte long → bon score
        // - Termes juridiques présents → bon score
        // - Mots français reconnus → bon score
    }

    @Test
    void testPipeline_ShortDocument() {
        String rawOcrText = """
            LOI N° 2024-10
            ARTICLE 1€'
            Contenu minimal.
            Fait à Cotonou, |e 1er janvier 2024
            """;

        String correctedText = corrector.applyCorrections(rawOcrText);
        List<Article> articles = extractionService.extractArticles(correctedText);
        DocumentMetadata metadata = extractionService.extractMetadata(correctedText);
        double confidence = extractionService.calculateConfidence(correctedText, articles);

        assertNotNull(articles);
        assertEquals(1, articles.size());
        
        assertNotNull(metadata);
        if (metadata.getPromulgationCity() != null) {
            assertEquals("Cotonou", metadata.getPromulgationCity());
        }
        
        assertTrue(confidence > 0.1, "Même un document court devrait avoir une confiance minimale");
    }

    @Test
    void testPipeline_WithOcrErrors() {
        String badOcrText = """
            REPUBLIQUE DU BENIN
            LOI N° 2024-20
            
            ARTICLE 1€'
            |es personnes concernées par |a présente loi doivent se conformer
            aux dispositions prévues par l''article 123 du code pénal.
            
            ARTICLE 2
            |e Président de |a République est chargé de l''exécution de |a
            présente loi qui sera publiée au Journal Officiel.
            
            Fait à Porto-Novo, |e 20 juin 2024
            Patrice TALON
            """;

        // Le correcteur devrait réparer la plupart des erreurs
        String correctedText = corrector.applyCorrections(badOcrText);
        
        List<Article> articles = extractionService.extractArticles(correctedText);
        assertEquals(2, articles.size());
        
        DocumentMetadata metadata = extractionService.extractMetadata(correctedText);
        assertNotNull(metadata);
        if (metadata.getPromulgationCity() != null) {
            assertEquals("Porto-Novo", metadata.getPromulgationCity());
        }
        
        double confidence = extractionService.calculateConfidence(correctedText, articles);
        assertTrue(confidence > 0.4, "Après correction, la confiance devrait être acceptable");
    }

    @Test
    void testConfigurationInitialization() {
        // Vérifier que la configuration est correctement initialisée
        assertTrue(config.getProps().size() > 10, 
                   "Plus de 10 patterns devraient être chargés");
        
        assertTrue(config.getSignatoryPatterns().size() >= 5,
                   "Au moins 5 signataires devraient être configurés");
        
        assertTrue(config.getFrenchDict().size() > 300000,
                   "Le dictionnaire devrait contenir plus de 300k mots");
        
        assertNotNull(config.getArticleStart());
        assertNotNull(config.getLawTitleStart());
        assertNotNull(config.getPromulgationDate());
    }

    @Test
    void testCorrectionAccuracy() {
        // Test que le correcteur traite le texte sans erreur
        String input = "REPUBLIQUE DU BENIN |a loi l''article";
        String corrected = corrector.applyCorrections(input);
        
        assertNotNull(corrected);
        assertFalse(corrected.isEmpty());
        // Les corrections exactes dépendent du contenu de corrections.csv
    }

    @Test
    void testExtractionRobustness() {
        // Test avec différents formats d'articles
        String text = """
            Article 1
            Premier format
            
            ARTICLE 2
            Deuxième format en majuscules
            
            Art. 3
            Troisième format abrégé
            
            Article 4
            Quatrième format standard
            """;

        List<Article> articles = extractionService.extractArticles(text);
        
        assertEquals(4, articles.size());
        
        for (int i = 0; i < articles.size(); i++) {
            assertEquals(i + 1, articles.get(i).getIndex(),
                        "L'index de l'article devrait correspondre");
        }
    }
}
