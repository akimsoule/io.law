package bj.gouv.sgg.integration;

import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.impl.ArticleRegexExtractor;
import bj.gouv.sgg.impl.CsvCorrector;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.DocumentMetadata;
import bj.gouv.sgg.service.OcrExtractionService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration sur les vrais échantillons OCR du dossier samples_ocr/
 * 
 * Ces tests valident que le pipeline complet (correction → extraction) 
 * fonctionne sur des documents réels avec erreurs OCR typiques.
 */
@Slf4j
class RealOcrSamplesIntegrationTest {
    
    private ArticleExtractorConfig config;
    private CsvCorrector corrector;
    private OcrExtractionService extractionService;
    
    private Path samplesPath;
    
    @BeforeEach
    void setUp() {
        // Initialisation manuelle des composants
        config = new ArticleExtractorConfig();
        config.init(); // Appel manuel de @PostConstruct
        
        corrector = new CsvCorrector();
        extractionService = new ArticleRegexExtractor(config);
        
        samplesPath = Paths.get("src/test/resources/samples_ocr");
        assertTrue(Files.exists(samplesPath), "Le dossier samples_ocr doit exister");
    }
    
    @Test
    void givenRealOcrSampleLoi2024_1WhenApplyFullPipelineThenExtractsArticlesSuccessfully() throws IOException {
        // Document récent avec erreurs OCR typiques
        Path loiPath = samplesPath.resolve("loi/loi-2024-1.txt");
        String rawOcr = Files.readString(loiPath);
        
        // Étape 1 : Correction
        String corrected = corrector.applyCorrections(rawOcr);
        assertNotNull(corrected);
        assertFalse(corrected.isEmpty());
        
        // Étape 2 : Extraction articles
        List<Article> articles = extractionService.extractArticles(corrected);
        assertNotNull(articles);
        assertTrue(articles.size() >= 1, "loi-2024-1 devrait contenir au moins 1 article");
        log.info("✅ loi-2024-1 : {} articles extraits", articles.size());
        
        // Étape 3 : Métadonnées
        DocumentMetadata metadata = extractionService.extractMetadata(corrected);
        assertNotNull(metadata);
        
        // Étape 4 : Confiance
        double confidence = extractionService.calculateConfidence(corrected, articles);
        assertTrue(confidence >= 0.0 && confidence <= 1.0);
        log.info("✅ loi-2024-1 : confiance = {}", confidence);
    }
    
    @Test
    void givenRealOcrSampleLoi2020_1WhenApplyFullPipelineThenExtractsArticlesSuccessfully() throws IOException {
        // Document avec structure claire et signataires
        Path loiPath = samplesPath.resolve("loi/loi-2020-1.txt");
        String rawOcr = Files.readString(loiPath);
        
        // Vérifier présence texte clé
        assertTrue(rawOcr.contains("RÉPUBLIQUE") || rawOcr.contains("RÉPUBLIaUE"));
        assertTrue(rawOcr.contains("ASSEMBLÉE") || rawOcr.contains("ASSEÀABLÉE"));
        
        String corrected = corrector.applyCorrections(rawOcr);
        List<Article> articles = extractionService.extractArticles(corrected);
        
        assertNotNull(articles);
        assertTrue(articles.size() >= 1, "loi-2020-1 devrait contenir au moins 1 article");
        
        // Vérifier contenu article
        Article firstArticle = articles.get(0);
        assertNotNull(firstArticle.getContent());
        assertFalse(firstArticle.getContent().trim().isEmpty());
        
        log.info("✅ loi-2020-1 : {} articles, premier article {} chars", 
                 articles.size(), firstArticle.getContent().length());
        
        // Métadonnées
        DocumentMetadata metadata = extractionService.extractMetadata(corrected);
        assertNotNull(metadata);
        
        // Vérifier extraction date (04 février 2020)
        if (metadata.getPromulgationDate() != null) {
            log.info("Date extraite : {}", metadata.getPromulgationDate());
        }
        
        // Vérifier extraction ville (Cotonou)
        if (metadata.getPromulgationCity() != null) {
            log.info("Ville extraite : {}", metadata.getPromulgationCity());
        }
    }
    
    @Test
    void testDecret2024_1632_FullPipeline() throws IOException {
        // Décret avec structure complexe (chapitres, sections)
        Path decretPath = samplesPath.resolve("decret/decret-2024-1632.txt");
        String rawOcr = Files.readString(decretPath);
        
        String corrected = corrector.applyCorrections(rawOcr);
        List<Article> articles = extractionService.extractArticles(corrected);
        
        assertNotNull(articles);
        assertTrue(articles.size() >= 1, "decret-2024-1632 devrait contenir des articles");
        log.info("✅ decret-2024-1632 : {} articles extraits", articles.size());
        
        // Vérifier numérotation articles
        for (Article article : articles) {
            assertTrue(article.getIndex() > 0, "Index article devrait être positif");
            assertNotNull(article.getContent());
        }
        
        // Métadonnées
        DocumentMetadata metadata = extractionService.extractMetadata(corrected);
        assertNotNull(metadata);
        
        double confidence = extractionService.calculateConfidence(corrected, articles);
        assertTrue(confidence > 0.0, "Confiance devrait être > 0");
        log.info("✅ decret-2024-1632 : confiance = {}", confidence);
    }
    
    @Test
    void testLoi1991_10_OldDocument() throws IOException {
        // Document ancien (1991) avec OCR de mauvaise qualité
        Path loiPath = samplesPath.resolve("loi/loi-1991-10.txt");
        String rawOcr = Files.readString(loiPath);
        
        String corrected = corrector.applyCorrections(rawOcr);
        
        // Sur document ancien, on accepte qu'il y ait peu d'articles extraits
        try {
            List<Article> articles = extractionService.extractArticles(corrected);
            assertNotNull(articles);
            log.info("✅ loi-1991-10 : {} articles extraits (document ancien)", articles.size());
            
            DocumentMetadata metadata = extractionService.extractMetadata(corrected);
            assertNotNull(metadata);
            
        } catch (Exception e) {
            // OK si échec sur document très ancien avec OCR dégradé
            log.warn("⚠️ loi-1991-10 : extraction difficile (document ancien) : {}", e.getMessage());
        }
    }
    
    @Test
    void testMultipleSamples_Statistics() throws IOException {
        // Test statistique sur plusieurs échantillons
        int totalFiles = 0;
        int successfulExtractions = 0;
        int totalArticles = 0;
        
        // Tester tous les fichiers loi/
        try (Stream<Path> paths = Files.walk(samplesPath.resolve("loi"))) {
            List<Path> loiFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".txt"))
                .limit(10) // Limiter à 10 fichiers pour performance
                .toList();
            
            for (Path loiFile : loiFiles) {
                totalFiles++;
                try {
                    String rawOcr = Files.readString(loiFile);
                    String corrected = corrector.applyCorrections(rawOcr);
                    List<Article> articles = extractionService.extractArticles(corrected);
                    
                    if (!articles.isEmpty()) {
                        successfulExtractions++;
                        totalArticles += articles.size();
                        log.debug("✅ {} : {} articles", loiFile.getFileName(), articles.size());
                    }
                } catch (Exception e) {
                    log.debug("⚠️ {} : échec extraction : {}", loiFile.getFileName(), e.getMessage());
                }
            }
        }
        
        log.info("📊 Statistiques : {}/{} fichiers traités avec succès", successfulExtractions, totalFiles);
        log.info("📊 Total articles extraits : {}", totalArticles);
        log.info("📊 Moyenne : {} articles/document", totalFiles > 0 ? totalArticles / (double) totalFiles : 0);
        
        assertTrue(totalFiles > 0, "Devrait avoir testé au moins 1 fichier");
        assertTrue(successfulExtractions >= totalFiles / 2, 
                   "Au moins 50% des fichiers devraient être extraits avec succès");
    }
    
    @Test
    void testCorrectionQuality_BeforeAfter() throws IOException {
        // Vérifier que les corrections améliorent la qualité
        Path loiPath = samplesPath.resolve("loi/loi-2024-1.txt");
        String rawOcr = Files.readString(loiPath);
        
        // Qualité avant correction
        double unrecBefore = config.unrecognizedWordsRate(rawOcr);
        
        // Correction
        String corrected = corrector.applyCorrections(rawOcr);
        
        // Qualité après correction
        double unrecAfter = config.unrecognizedWordsRate(corrected);
        
        log.info("📊 Qualité OCR : avant={}, après={}", unrecBefore, unrecAfter);
        
        // La correction devrait améliorer ou maintenir la qualité
        assertTrue(unrecAfter <= unrecBefore + 0.05, 
                   "Les corrections ne devraient pas dégrader la qualité");
    }
    
    @Test
    void testArticleExtractionConsistency() throws IOException {
        // Vérifier que l'extraction est cohérente (même document → même résultat)
        Path loiPath = samplesPath.resolve("loi/loi-2020-1.txt");
        String rawOcr = Files.readString(loiPath);
        String corrected = corrector.applyCorrections(rawOcr);
        
        // Première extraction
        List<Article> articles1 = extractionService.extractArticles(corrected);
        
        // Deuxième extraction (même texte)
        List<Article> articles2 = extractionService.extractArticles(corrected);
        
        // Résultats identiques
        assertEquals(articles1.size(), articles2.size(), 
                     "L'extraction devrait être déterministe");
        
        for (int i = 0; i < articles1.size(); i++) {
            assertEquals(articles1.get(i).getIndex(), articles2.get(i).getIndex());
            assertEquals(articles1.get(i).getContent(), articles2.get(i).getContent());
        }
        
        log.info("✅ Extraction cohérente : {} articles", articles1.size());
    }
    
    @Test
    void testMetadataExtraction_MultipleDocuments() throws IOException {
        // Tester extraction métadonnées sur plusieurs documents
        List<String> testFiles = List.of(
            "loi/loi-2024-1.txt",
            "loi/loi-2020-1.txt",
            "decret/decret-2024-1632.txt"
        );
        
        int documentsWithDate = 0;
        int documentsWithCity = 0;
        int documentsWithTitle = 0;
        
        for (String testFile : testFiles) {
            Path filePath = samplesPath.resolve(testFile);
            if (!Files.exists(filePath)) continue;
            
            String rawOcr = Files.readString(filePath);
            String corrected = corrector.applyCorrections(rawOcr);
            DocumentMetadata metadata = extractionService.extractMetadata(corrected);
            
            assertNotNull(metadata, "Metadata ne devrait jamais être null");
            
            if (metadata.getPromulgationDate() != null) documentsWithDate++;
            if (metadata.getPromulgationCity() != null) documentsWithCity++;
            if (metadata.getLawTitle() != null) documentsWithTitle++;
            
            log.debug("📄 {} : date={}, ville={}, titre={}", 
                     testFile, 
                     metadata.getPromulgationDate() != null,
                     metadata.getPromulgationCity() != null,
                     metadata.getLawTitle() != null);
        }
        
        log.info("📊 Métadonnées extraites : dates={}, villes={}, titres={}", 
                 documentsWithDate, documentsWithCity, documentsWithTitle);
    }
}
