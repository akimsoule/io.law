package bj.gouv.sgg.ai.integration;

import bj.gouv.sgg.TestApplication;
import bj.gouv.sgg.ai.service.AIOrchestrator;
import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.model.LawDocument;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration du AIOrchestrator avec un vrai PDF.
 * 
 * <p><b>Objectif</b> : Tester le flux complet de transformation
 * PDF → OCR → Correction IA → JSON avec chunking automatique.</p>
 * 
 * <p><b>Scénarios testés</b> :
 * <ul>
 *   <li>Correction OCR avec chunking automatique</li>
 *   <li>Extraction JSON depuis texte OCR corrigé</li>
 *   <li>Flux complet sur vrai document (decret-2024-1632)</li>
 *   <li>Validation structure JSON générée</li>
 * </ul>
 * 
 * <p><b>Prérequis</b> :
 * <ul>
 *   <li>Ollama installé : brew install ollama</li>
 *   <li>Ollama démarré : ollama serve</li>
 *   <li>Modèle téléchargé : ollama pull gemma3n</li>
 *   <li>Variable d'environnement : OLLAMA_AVAILABLE=true</li>
 *   <li>Fichiers OCR dans data/ocr/decret/</li>
 * </ul>
 */
@Slf4j
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DisplayName("AIOrchestrator PDF Integration Tests")
@EnabledIfEnvironmentVariable(named = "OLLAMA_AVAILABLE", matches = "true")
class AIOrchestratorPdfIntegrationTest {

    private static final Path TEST_DATA_DIR = Paths.get("src/test/resources/data");
    private static final Path TEST_RESULT_DIR = Paths.get("src/test/resources/result");

    @Autowired
    private AIOrchestrator orchestrator;

    private LawDocument testDocument;

    @BeforeEach
    void setUp() throws IOException {
        testDocument = new LawDocument();
        testDocument.setType("decret");
        testDocument.setYear(2024);
        testDocument.setNumber(1632);
        testDocument.setStatus(LawDocument.ProcessingStatus.DOWNLOADED);
        
        // Créer le répertoire de résultats s'il n'existe pas
        Files.createDirectories(TEST_RESULT_DIR);
        
        log.info("🔧 AIOrchestrator initialisé pour test : {}", testDocument.getDocumentId());
        log.info("📂 Test data dir: {}", TEST_DATA_DIR.toAbsolutePath());
        log.info("📂 Result dir: {}", TEST_RESULT_DIR.toAbsolutePath());
    }

    @Test
    @DisplayName("L'orchestrateur doit avoir au moins un provider IA disponible")
    void orchestratorShouldHaveProviderAvailable() {
        // When
        boolean hasProvider = orchestrator.hasAnyProviderAvailable();
        var providers = orchestrator.getAvailableProviders();

        // Then
        assertThat(hasProvider).isTrue();
        assertThat(providers).isNotEmpty();
        
        log.info("✅ Providers disponibles : {}", providers.size());
        providers.forEach(p -> log.info("  - {} (available: {})", 
                p.getProviderName(), p.isAvailable()));
    }

    @Test
    @DisplayName("L'orchestrateur doit avoir les transformations disponibles")
    void orchestratorShouldHaveTransformationsAvailable() {
        // When
        boolean ocrCorrection = orchestrator.isTransformationAvailable("OCR_CORRECTION");
        boolean ocrToJson = orchestrator.isTransformationAvailable("OCR_TO_JSON");

        // Then
        assertThat(ocrCorrection).isTrue();
        assertThat(ocrToJson).isTrue();
        
        log.info("✅ Transformations disponibles : OCR_CORRECTION={}, OCR_TO_JSON={}", 
                ocrCorrection, ocrToJson);
    }

    @Test
    @DisplayName("L'orchestrateur doit corriger un texte OCR court")
    void orchestratorShouldCorrectShortOcr() throws IAException, IOException {
        // Given
        String rawOcr = "Articlc 1cr : La prćsente loi portc sur la réglcmentation.";

        // When
        long startTime = System.currentTimeMillis();
        String corrected = orchestrator.correctOcr(testDocument, rawOcr);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(corrected)
                .isNotBlank()
                .contains("Article")
                .doesNotContain("Articlc");
        
        // Sauvegarder résultat
        saveOcrResult("test-short", corrected, "corrected");
        
        log.info("✅ OCR court corrigé en {}ms", duration);
        log.info("📄 Entrée : {}", rawOcr);
        log.info("📝 Sortie : {}", corrected.trim());
    }

    @Test
    @DisplayName("L'orchestrateur doit corriger un texte OCR volumineux avec chunking")
    void orchestratorShouldCorrectLargeOcrWithChunking() throws IAException, IOException {
        // Given - Texte volumineux avec erreurs OCR (~6000 chars)
        String largeOcr = generateLargeOcrWithErrors(6000);
        int originalErrors = countOcrErrors(largeOcr);

        // When
        long startTime = System.currentTimeMillis();
        String corrected = orchestrator.correctOcr(testDocument, largeOcr);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(corrected).isNotBlank();
        assertThat(corrected.length()).isGreaterThan(largeOcr.length() / 2);
        
        int remainingErrors = countOcrErrors(corrected);
        int correctedErrors = originalErrors - remainingErrors;
        double correctionRate = (correctedErrors * 100.0) / originalErrors;
        
        assertThat(remainingErrors).isLessThan(originalErrors);
        
        // Sauvegarder résultats
        saveOcrResult("test-large", largeOcr, "original");
        saveOcrResult("test-large", corrected, "corrected_chunked");
        
        log.info("✅ OCR volumineux corrigé en {}ms avec chunking automatique", duration);
        log.info("📏 Taille : {} → {} chars", largeOcr.length(), corrected.length());
        log.info("🔧 Erreurs OCR : {} détectées, {} corrigées ({:.1f}%)", 
                originalErrors, correctedErrors, correctionRate);
    }

    @Test
    @DisplayName("L'orchestrateur doit extraire JSON depuis texte OCR")
    void orchestratorShouldExtractJsonFromOcr() throws IAException, IOException {
        // Given
        String ocrText = """
                Décret n° 2024-1632 du 15 octobre 2024
                
                Article 1er.
                La présente loi porte sur la réglementation des activités.
                
                Article 2.
                Les dispositions du présent décret s'appliquent à tous.
                
                Fait à Porto-Novo, le 15 octobre 2024
                Le Président de la République,
                Patrice TALON
                """;

        // When
        long startTime = System.currentTimeMillis();
        JsonObject json = orchestrator.ocrToJson(testDocument, ocrText);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(json).isNotNull();
        
        // Vérifier structure JSON de base
        boolean hasArticles = json.has("articles") && json.get("articles").isJsonArray();
        
        // Sauvegarder résultats
        saveOcrResult("test-json-extract", ocrText, "original");
        saveJsonResult("test-json-extract", json, "extracted");
        
        log.info("✅ JSON extrait en {}ms", duration);
        log.info("📄 Structure : hasArticles={}", hasArticles);
        
        if (hasArticles) {
            JsonArray articles = json.getAsJsonArray("articles");
            log.info("📊 {} articles extraits", articles.size());
            assertThat(articles.size()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("L'orchestrateur doit traiter un vrai fichier OCR (decret-2024-1381.txt)")
    void orchestratorShouldProcessRealOcrFile() throws IOException, IAException {
        // Given - Charger le fichier OCR depuis test/resources/data
        Path ocrPath = TEST_DATA_DIR.resolve("decret-2024-1381.txt");
        
        if (!Files.exists(ocrPath)) {
            log.warn("⏭️ Fichier OCR non trouvé : {}, test ignoré", ocrPath);
            return;
        }

        String rawOcr = Files.readString(ocrPath);
        testDocument.setNumber(1381); // Adapter le numéro
        
        log.info("📄 Test avec fichier réel : {}", ocrPath.getFileName());
        log.info("📏 Taille brute : {} chars", rawOcr.length());

        // When - Étape 1 : Correction OCR
        long step1Start = System.currentTimeMillis();
        String correctedOcr = orchestrator.correctOcr(testDocument, rawOcr);
        long step1Duration = System.currentTimeMillis() - step1Start;
        
        log.info("✅ Étape 1/2 : OCR corrigé en {}ms ({} → {} chars)", 
                step1Duration, rawOcr.length(), correctedOcr.length());

        // Sauvegarder OCR corrigé
        saveOcrResult("decret-2024-1381", correctedOcr, "corrected");

        // When - Étape 2 : Extraction JSON
        long step2Start = System.currentTimeMillis();
        JsonObject json = orchestrator.ocrToJson(testDocument, correctedOcr);
        long step2Duration = System.currentTimeMillis() - step2Start;
        
        log.info("✅ Étape 2/2 : JSON extrait en {}ms", step2Duration);

        // Sauvegarder JSON extrait
        saveJsonResult("decret-2024-1381", json, "extracted");

        // Then - Validation structure JSON
        assertThat(correctedOcr).isNotBlank();
        assertThat(json).isNotNull();
        
        long totalDuration = step1Duration + step2Duration;
        log.info("🎉 Flux complet terminé en {}ms ({} + {})", 
                totalDuration, step1Duration, step2Duration);
        
        // Analyse structure JSON
        if (json.has("articles") && json.get("articles").isJsonArray()) {
            JsonArray articles = json.getAsJsonArray("articles");
            log.info("📊 {} articles extraits", articles.size());
            assertThat(articles.size()).isGreaterThan(0);
        }
        
        if (json.has("_metadata")) {
            JsonObject metadata = json.getAsJsonObject("_metadata");
            log.info("📋 Métadonnées : documentId={}, confidence={}", 
                    metadata.has("documentId") ? metadata.get("documentId").getAsString() : "N/A",
                    metadata.has("confidence") ? metadata.get("confidence").getAsDouble() : 0.0);
        }
        
        if (json.has("signatories") && json.get("signatories").isJsonArray()) {
            JsonArray signatories = json.getAsJsonArray("signatories");
            log.info("✍️ {} signataires extraits", signatories.size());
        }
    }

    @Test
    @DisplayName("L'orchestrateur doit gérer un texte vide sans erreur")
    void orchestratorShouldHandleEmptyTextGracefully() throws IAException {
        // Given
        String emptyText = "";

        // When & Then - Ne doit pas lever d'exception
        String corrected = orchestrator.correctOcr(testDocument, emptyText);
        
        assertThat(corrected).isNotNull();
        log.info("✅ Texte vide géré sans erreur");
    }

    @Test
    @DisplayName("L'orchestrateur doit gérer un texte sans erreur OCR")
    void orchestratorShouldHandleCleanTextWithoutOcrErrors() throws IAException, IOException {
        // Given - Texte propre sans erreurs OCR
        String cleanText = """
                Article 1er.
                La présente loi porte sur la réglementation.
                
                Article 2.
                Les dispositions s'appliquent à tous.
                """;

        // When
        String corrected = orchestrator.correctOcr(testDocument, cleanText);

        // Then - Le texte doit être préservé ou légèrement amélioré
        assertThat(corrected)
                .isNotBlank()
                .contains("Article");
        
        // Sauvegarder résultats
        saveOcrResult("test-clean", cleanText, "original");
        saveOcrResult("test-clean", corrected, "corrected");
        
        log.info("✅ Texte propre traité sans dégradation");
        log.info("📄 Entrée : {} chars", cleanText.length());
        log.info("📝 Sortie : {} chars", corrected.length());
    }

    @Test
    @DisplayName("L'orchestrateur doit traiter plusieurs documents séquentiellement")
    void orchestratorShouldProcessMultipleDocumentsSequentially() throws IAException, IOException {
        // Given
        String[] texts = {
                "Articlc 1er : Test 1.",
                "Articlc 2 : Test 2.",
                "Articlc 3 : Test 3."
        };

        // When & Then
        for (int i = 0; i < texts.length; i++) {
            testDocument.setNumber(1632 + i);
            String corrected = orchestrator.correctOcr(testDocument, texts[i]);
            
            assertThat(corrected)
                    .isNotBlank()
                    .contains("Article");
            
            // Sauvegarder chaque résultat
            saveOcrResult("test-sequential-" + (i+1), texts[i], "original");
            saveOcrResult("test-sequential-" + (i+1), corrected, "corrected");
            
            log.info("✅ Document {} traité : {}", i + 1, testDocument.getDocumentId());
        }
        
        log.info("✅ {} documents traités séquentiellement", texts.length);
    }

    // ==================== Méthodes utilitaires ====================

    /**
     * Sauvegarde un résultat OCR dans result/ (sans timestamp)
     */
    private void saveOcrResult(String baseFilename, String content, String suffix) throws IOException {
        String filename = String.format("%s_%s.txt", baseFilename, suffix);
        Path resultPath = TEST_RESULT_DIR.resolve(filename);
        Files.writeString(resultPath, content);
        log.info("💾 OCR sauvegardé : {}", resultPath.getFileName());
    }

    /**
     * Sauvegarde un résultat JSON dans result/ (sans timestamp)
     */
    private void saveJsonResult(String baseFilename, JsonObject json, String suffix) throws IOException {
        String filename = String.format("%s_%s.json", baseFilename, suffix);
        Path resultPath = TEST_RESULT_DIR.resolve(filename);
        Files.writeString(resultPath, json.toString());
        log.info("💾 JSON sauvegardé : {}", resultPath.getFileName());
    }

    private String generateLargeOcrWithErrors(int targetSize) {
        StringBuilder sb = new StringBuilder();
        int articleNum = 1;
        
        while (sb.length() < targetSize) {
            // Simuler erreurs OCR typiques
            sb.append(String.format("Articlc %d.%n%n", articleNum));
            sb.append(String.format("La prćsente loi portc sur la réglcmentation des activitćs.%n"));
            sb.append(String.format("Les dispositions du prćsent articlc s'appliquent à tous les citoyens.%n"));
            sb.append(String.format("Sont abrogćes toutes dispositions antćrieures contraircs.%n%n"));
            articleNum++;
        }
        
        return sb.toString();
    }

    private int countOcrErrors(String text) {
        int count = 0;
        String[] errors = {
                "Articlc", "prćsent", "portc", "réglcmentation", 
                "activitćs", "articlc", "abrogćes", "antćrieures", "contraircs"
        };
        
        for (String error : errors) {
            int index = 0;
            while ((index = text.indexOf(error, index)) != -1) {
                count++;
                index += error.length();
            }
        }
        
        return count;
    }
}
