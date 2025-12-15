package bj.gouv.sgg.qa.service.impl;

import bj.gouv.sgg.qa.service.OcrQualityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration avec un vrai fichier OCR.
 * 
 * <p>Ce test nécessite l'accès au répertoire law-ocr-json/src/test/resources
 * et ne s'exécute que si le fichier existe.
 */
@SpringBootTest(classes = {
    OcrQualityServiceImpl.class, 
    UnrecognizedWordsServiceImpl.class
})
class OcrStructureValidationIntegrationTest {
    
    private static final Logger log = LoggerFactory.getLogger(OcrStructureValidationIntegrationTest.class);
    
    @Autowired
    private OcrQualityService ocrQualityService;
    
    static boolean ocrFileExists() {
        Path ocrFile = Paths.get("../law-ocr-json/src/test/resources/samples_ocr/loi/loi-2009-1.txt");
        return Files.exists(ocrFile);
    }
    
    @Test
    @EnabledIf("ocrFileExists")
    void shouldValidateRealOcrFileLoi20091() throws IOException {
        // Given : Fichier OCR réel
        Path ocrFile = Paths.get("../law-ocr-json/src/test/resources/samples_ocr/loi/loi-2009-1.txt");
        String ocrContent = Files.readString(ocrFile, StandardCharsets.UTF_8);
        
        // When
        double structureScore = ocrQualityService.validateDocumentStructure(ocrContent);
        
        // Then : Document réel peut avoir des sections manquantes ou mal formées
        // Test pragmatique : accepter >= 0.6 (3 sections / 5 minimum)
        assertThat(structureScore).isGreaterThanOrEqualTo(0.6);
        
        log.info("📋 Score structure loi-2009-1.txt : {}", structureScore);
        
        // Vérifications détaillées des sections présentes
        assertThat(ocrContent).containsIgnoringCase("REPUBLIQUE DU BENIN");
        assertThat(ocrContent).containsIgnoringCase("PRESIDENCE");
        assertThat(ocrContent).containsIgnoringCase("LOI N");
        assertThat(ocrContent).containsIgnoringCase("L'Assemblée nationale a délibéré");
        assertThat(ocrContent).containsIgnoringCase("sera exécutée comme Loi de l'Etat");
        assertThat(ocrContent).containsIgnoringCase("Fait a Cotonou"); // OCR : "a" sans accent
        assertThat(ocrContent).containsIgnoringCase("AMPLIATIONS");
    }
}
