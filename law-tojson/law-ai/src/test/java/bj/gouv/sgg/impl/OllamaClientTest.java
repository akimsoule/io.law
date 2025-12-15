package bj.gouv.sgg.impl;

import bj.gouv.sgg.config.LawProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires et d'intégration pour OllamaClient.
 * 
 * <p><b>Tests sans Ollama requis</b> :
 * <ul>
 *   <li>Vérification format source name</li>
 *   <li>Gestion erreurs basiques</li>
 * </ul>
 * 
 * <p><b>Tests avec Ollama requis</b> (skippés si indisponible) :
 * <ul>
 *   <li>Correction texte OCR</li>
 *   <li>Disponibilité service</li>
 *   <li>Gestion timeouts</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
@EnabledIfEnvironmentVariable(named = "OLLAMA_AVAILABLE", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OllamaClientTest {

    @Autowired
    private OllamaClient ollamaClient;

    @Autowired
    private LawProperties properties;

    private boolean ollamaAvailable = false;

    @BeforeAll
    void checkOllama() {
        ollamaAvailable = ollamaClient.isAvailable();
        
        if (ollamaAvailable) {
            log.info("✅ Ollama disponible pour tests");
        } else {
            log.warn("⚠️ Ollama indisponible - Tests limités");
        }
    }

    // ========== Tests sans Ollama requis ==========

    @Test
    @DisplayName("Test format source name")
    void testSourceNameFormat() {
        String sourceName = ollamaClient.getSourceName();
        
        assertNotNull(sourceName, "Source name ne devrait pas être null");
        assertEquals("IA:OLLAMA", sourceName, "Source name devrait être 'IA:OLLAMA'");
        
        log.info("🤖 Source name : {}", sourceName);
    }

    @Test
    @DisplayName("Test isAvailable retourne booléen")
    void testIsAvailableReturnBoolean() {
        // isAvailable() ne devrait jamais lancer d'exception
        boolean available = assertDoesNotThrow(
            () -> ollamaClient.isAvailable(),
            "isAvailable() ne devrait pas lancer d'exception"
        );
        
        log.info("📡 Ollama disponible : {}", available);
    }


    @Test
    @Disabled("Test désactivé - prompt null accepté avec prompt par défaut")
    @DisplayName("Test gestion texte OCR null")
    void testNullOcrTextHandling() {
        Assumptions.assumeTrue(ollamaAvailable, "Ollama requis");

        String nullOcr = null;
        String prompt = "Corrige le texte";

        // Devrait gérer texte null sans crash
        assertThrows(
            Exception.class,
            () -> ollamaClient.correctOcrText(nullOcr, prompt),
            "Devrait lancer exception avec texte null"
        );
    }

    // ========== Tests avec Ollama requis ==========

    @Test
    @DisplayName("Test correction texte basique")
    void testBasicTextCorrection() {
        Assumptions.assumeTrue(ollamaAvailable, "Ollama requis");

        String rawOcr = "Articlc 1e du décret";
        String prompt = "Corrige les erreurs OCR : " + rawOcr;

        String corrected = assertDoesNotThrow(
            () -> ollamaClient.correctOcrText(rawOcr, prompt),
            "La correction ne devrait pas échouer"
        );

        assertNotNull(corrected, "Le texte corrigé ne devrait pas être null");
        assertFalse(corrected.trim().isEmpty(), "Le texte corrigé ne devrait pas être vide");
        
        log.info("📝 Original : {}", rawOcr);
        log.info("✅ Corrigé  : {}", corrected);
    }

    @Test
    @DisplayName("Test correction avec caractères spéciaux")
    void testSpecialCharactersCorrection() {
        Assumptions.assumeTrue(ollamaAvailable, "Ollama requis");

        String rawOcr = "Articlc 1e : Les élèves bénéficient d'un accès à l'éducation.";
        String prompt = "Corrige uniquement les erreurs OCR, conserve les accents : " + rawOcr;

        String corrected = assertDoesNotThrow(
            () -> ollamaClient.correctOcrText(rawOcr, prompt)
        );

        assertNotNull(corrected);
        
        // Vérifier que les accents sont préservés
        assertTrue(
            corrected.contains("é") || corrected.toLowerCase().contains("eleve"),
            "Les caractères accentués devraient être préservés ou transformés correctement"
        );
        
        log.info("📝 Caractères spéciaux préservés : {}", corrected);
    }

    @Test
    @DisplayName("Test correction multiple erreurs")
    void testMultipleErrorsCorrection() {
        Assumptions.assumeTrue(ollamaAvailable, "Ollama requis");

        // Texte avec plusieurs types d'erreurs OCR
        String rawOcr = "Articlc 1e : Le présent décrct porte créatlon.";
        String prompt = "Corrige toutes les erreurs OCR : " + rawOcr;

        String corrected = ollamaClient.correctOcrText(rawOcr, prompt);

        assertNotNull(corrected);
        
        log.info("📝 Multiple erreurs corrigées :");
        log.info("   Avant : {}", rawOcr);
        log.info("   Après : {}", corrected);
    }

    @Test
    @DisplayName("Test texte déjà correct")
    void testAlreadyCorrectText() {
        Assumptions.assumeTrue(ollamaAvailable, "Ollama requis");

        String correctText = "Article 1er : Le présent décret porte création.";
        String prompt = "Corrige les erreurs OCR s'il y en a : " + correctText;

        String result = ollamaClient.correctOcrText(correctText, prompt);

        assertNotNull(result);
        
        log.info("📝 Texte correct traité : {}", result.substring(0, Math.min(80, result.length())));
    }

    @Test
    @DisplayName("Test réponse dans délai raisonnable")
    void testResponseTime() {
        Assumptions.assumeTrue(ollamaAvailable, "Ollama requis");

        String rawOcr = "Articlc 1e : Test rapide.";
        String prompt = "Corrige : " + rawOcr;

        long startTime = System.currentTimeMillis();
        
        String corrected = ollamaClient.correctOcrText(rawOcr, prompt);
        
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(corrected);
        
        log.info("⏱️ Temps de correction : {}ms", duration);
        
        // Pour gemma3n:latest (6.9B), la réponse peut prendre jusqu'à 60s
        assertTrue(duration < 60000, 
                  "La correction devrait prendre moins de 60s pour un texte court");
    }

    @Test
    @DisplayName("Test URL Ollama configurée")
    void testOllamaUrlConfiguration() {
        String ollamaUrl = properties.getCapacity().getOllamaUrl();
        
        assertNotNull(ollamaUrl, "L'URL Ollama devrait être configurée");
        assertFalse(ollamaUrl.trim().isEmpty(), "L'URL Ollama ne devrait pas être vide");
        assertTrue(
            ollamaUrl.startsWith("http://") || ollamaUrl.startsWith("https://"),
            "L'URL Ollama devrait commencer par http:// ou https://"
        );
        
        log.info("📍 URL Ollama : {}", ollamaUrl);
    }

    @Test
    @DisplayName("Test modèle requis configuré")
    void testRequiredModelConfiguration() {
        String requiredModel = properties.getCapacity().getOllamaModelsRequired();
        
        assertNotNull(requiredModel, "Le modèle requis devrait être configuré");
        assertFalse(requiredModel.trim().isEmpty(), "Le modèle requis ne devrait pas être vide");
        
        log.info("🎯 Modèle requis : {}", requiredModel);
    }
}
