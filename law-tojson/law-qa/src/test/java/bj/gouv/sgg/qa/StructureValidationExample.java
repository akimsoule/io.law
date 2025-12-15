package bj.gouv.sgg.qa;

import bj.gouv.sgg.qa.service.OcrQualityService;
import bj.gouv.sgg.qa.service.impl.OcrQualityServiceImpl;
import bj.gouv.sgg.qa.service.impl.UnrecognizedWordsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de validation de structure OCR.
 * 
 * <p>Vérifie la présence des 5 parties obligatoires d'un document de loi.
 */
class StructureValidationTest {
    
    private OcrQualityService ocrQualityService;
    
    @BeforeEach
    void setUp() {
        UnrecognizedWordsServiceImpl unrecognizedWordsService = new UnrecognizedWordsServiceImpl();
        ocrQualityService = new OcrQualityServiceImpl(unrecognizedWordsService);
    }
    
    @Test
    void givenRealOcrFileWhenValidateStructureThenReturnsValidScore() throws IOException {
        // Charger un fichier OCR réel
        Path ocrFile = Paths.get("../law-ocr-json/src/test/resources/samples_ocr/loi/loi-2009-1.txt");
        
        if (!Files.exists(ocrFile)) {
            fail("Fichier OCR introuvable : " + ocrFile.toAbsolutePath());
        }
        
        String ocrContent = Files.readString(ocrFile, StandardCharsets.UTF_8);
        
        // Valider la structure
        double structureScore = ocrQualityService.validateDocumentStructure(ocrContent);
        
        // Assertions
        assertTrue(structureScore >= 0.0 && structureScore <= 1.0, 
            "Le score doit être entre 0.0 et 1.0");
        
        // Vérifier que le document a au moins quelques sections
        assertTrue(structureScore >= 0.4, 
            "Le document devrait avoir au moins 2 sections sur 5 (score >= 0.4)");
    }
    
    @Test
    void givenCompleteDocumentWhenValidateStructureThenReturnsScoreOne() {
        // Document avec toutes les sections
        String completeDoc = """
            REPUBLIQUE DU BENIN
            Fraternité - Justice - Travail
            PRESIDENCE DE LA REPUBLIQUE
            
            LOI N° 2024-15
            
            L'Assemblée Nationale a délibéré et adopté
            
            Article 1er
            La présente loi porte...
            
            La présente loi sera exécutée comme loi de l'État.
            
            Fait à Cotonou, le 15 mars 2024
            AMPLIATIONS
            """;
        
        double score = ocrQualityService.validateDocumentStructure(completeDoc);
        
        assertEquals(1.0, score, 0.01, "Document complet devrait avoir score = 1.0");
    }
    
    @Test
    void givenIncompleteDocumentWhenValidateStructureThenReturnsLowerScore() {
        // Document avec seulement 1 section sur 5 (juste un article, pas d'entête complet)
        String incompleteDoc = """
            REPUBLIQUE DU BENIN
            
            Article 1er
            Contenu de l'article
            """;
        
        double score = ocrQualityService.validateDocumentStructure(incompleteDoc);
        
        // Entête incomplet (manque devise et PRESIDENCE) = 0 sections détectées
        // Titre manquant (pas de "LOI N°") = 0
        // Visa manquant = 0
        // Fin de corps manquante = 0
        // Pied manquant = 0
        // Score attendu : 0/5 = 0.0
        assertEquals(0.0, score, 0.01, "Document incomplet (sans entête complet) devrait avoir score proche de 0.0");
    }
    
    @Test
    void givenEmptyContentWhenValidateStructureThenReturnsScoreZero() {
        double score = ocrQualityService.validateDocumentStructure("");
        
        assertEquals(0.0, score, "Document vide devrait avoir score = 0.0");
    }
}
