package bj.gouv.sgg.service;

import bj.gouv.sgg.impl.OllamaClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires de base pour l'interface IAService.
 * 
 * <p>Valide que les implémentations respectent le contrat de l'interface.
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class IAServiceTest {

    @Autowired
    private IAService iaService;

    @Test
    @DisplayName("Test bean IAService injecté")
    void testIAServiceBeanInjected() {
        assertNotNull(iaService, "Le bean IAService devrait être injecté");
        log.info("✅ Bean IAService : {}", iaService.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Test méthode getSourceName existe")
    void testGetSourceNameExists() {
        assertNotNull(iaService, "Le service ne devrait pas être null");
        
        String sourceName = iaService.getSourceName();
        
        assertNotNull(sourceName, "getSourceName() ne devrait pas retourner null");
        assertFalse(sourceName.trim().isEmpty(), "getSourceName() ne devrait pas retourner vide");
        
        log.info("🤖 Source IA : {}", sourceName);
    }

    @Test
    @DisplayName("Test méthode isAvailable existe")
    void testIsAvailableExists() {
        assertNotNull(iaService, "Le service ne devrait pas être null");
        
        // isAvailable() ne devrait jamais lancer d'exception
        boolean available = assertDoesNotThrow(
            () -> iaService.isAvailable(),
            "isAvailable() ne devrait pas lancer d'exception"
        );
        
        log.info("📡 IA disponible : {}", available);
    }

    @Test
    @DisplayName("Test implémentation est OllamaClient")
    void testImplementationIsOllamaClient() {
        assertNotNull(iaService, "Le service ne devrait pas être null");
        
        assertTrue(
            iaService instanceof OllamaClient,
            "L'implémentation devrait être OllamaClient"
        );
        
        log.info("✅ Implémentation correcte : OllamaClient");
    }

    @Test
    @DisplayName("Test format source name commence par IA:")
    void testSourceNameFormat() {
        String sourceName = iaService.getSourceName();
        
        assertTrue(
            sourceName.startsWith("IA:"),
            "Le source name devrait commencer par 'IA:'"
        );
        
        log.info("✅ Format source name valide : {}", sourceName);
    }
}
