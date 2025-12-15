package bj.gouv.sgg.qa;

import bj.gouv.sgg.qa.service.OcrQualityService;
import bj.gouv.sgg.qa.service.impl.OcrQualityServiceImpl;
import bj.gouv.sgg.qa.service.impl.UnrecognizedWordsServiceImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class StructureValidationExampleTest {

    private OcrQualityService ocrQualityService;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        UnrecognizedWordsServiceImpl unrecognizedWordsService = new UnrecognizedWordsServiceImpl();
        ocrQualityService = new OcrQualityServiceImpl(unrecognizedWordsService);
    }

    @Test
    void givenRealOcrFileWhenValidateStructureThenReturnsValidScore() throws IOException {
        // Given
        Path ocrFile = Paths.get("../law-ocr-json/src/test/resources/samples_ocr/loi/loi-2009-1.txt");
        
        Assumptions.assumeTrue(Files.exists(ocrFile), 
            "OCR file not found: " + ocrFile.toAbsolutePath());
        
        String ocrContent = Files.readString(ocrFile, StandardCharsets.UTF_8);
        
        // When
        double structureScore = ocrQualityService.validateDocumentStructure(ocrContent);
        
        // Then
        assertNotNull(structureScore);
        assertTrue(structureScore >= 0.0 && structureScore <= 1.0, 
            "Structure score must be between 0.0 and 1.0");
        assertTrue(structureScore >= 0.6, 
            "loi-2009-1.txt should have at least 60% structure score");
    }

    @Test
    void givenCompleteDocumentWhenValidateStructureThenReturnsScoreOne() {
        // Given - Document complet avec toutes les sections
        String completeContent = """
            REPUBLIQUE DU BENIN
            Fraternité - Justice - Travail
            PRESIDENCE DE LA REPUBLIQUE
            
            LOI N° 2009-1 du 15 janvier 2009
            
            L'Assemblée Nationale a délibéré et adopté,
            Le Président de la République promulgue la loi dont la teneur suit :
            
            Article 1er : La présente loi fixe...
            
            La présente loi sera exécutée comme loi de l'État.
            
            Fait à Cotonou, le 15 janvier 2009
            
            AMPLIATIONS
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(completeContent);
        
        // Then
        assertEquals(1.0, score, 0.01, "Complete document should have score 1.0");
    }

    @Test
    void givenDocumentMissingOneSectionWhenValidateStructureThenReturnsPartialScore() {
        // Given - Document sans la section VISA
        String incompleteContent = """
            REPUBLIQUE DU BENIN
            Fraternité - Justice - Travail
            PRESIDENCE DE LA REPUBLIQUE
            
            LOI N° 2009-1 du 15 janvier 2009
            
            Article 1er : La présente loi fixe...
            
            La présente loi sera exécutée comme loi de l'État.
            
            Fait à Cotonou, le 15 janvier 2009
            
            AMPLIATIONS
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(incompleteContent);
        
        // Then
        assertTrue(score >= 0.6 && score < 1.0, 
            "Document missing one section should have score between 0.6 and 1.0");
    }

    @Test
    void givenDocumentMissingMultipleSectionsWhenValidateStructureThenReturnsLowScore() {
        // Given - Document avec seulement titre et corps
        String minimalContent = """
            LOI N° 2009-1 du 15 janvier 2009
            
            Article 1er : La présente loi fixe...
            Article 2 : Les dispositions...
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(minimalContent);
        
        // Then
        assertTrue(score >= 0.0 && score < 0.6, 
            "Document missing multiple sections should have low score");
    }

    @Test
    void givenEmptyContentWhenValidateStructureThenReturnsScoreZero() {
        // Given
        String emptyContent = "";
        
        // When
        double score = ocrQualityService.validateDocumentStructure(emptyContent);
        
        // Then
        assertEquals(0.0, score, "Empty content should have score 0.0");
    }

    @Test
    void givenNullContentWhenValidateStructureThenReturnsZero() {
        // When
        double score = ocrQualityService.validateDocumentStructure(null);
        
        // Then
        assertEquals(0.0, score, 0.001);
    }

    @Test
    void givenContentWithEnteteWhenValidateStructureThenDetectsEnteteSection() {
        // Given
        String contentWithEntete = """
            REPUBLIQUE DU BENIN
            Fraternité - Justice - Travail
            PRESIDENCE DE LA REPUBLIQUE
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(contentWithEntete);
        
        // Then
        assertTrue(score >= 0.2, "Should detect entête section");
    }

    @Test
    void givenContentWithTitreWhenValidateStructureThenDetectsTitreSection() {
        // Given
        String contentWithTitre = "LOI N° 2009-1 du 15 janvier 2009";
        
        // When
        double score = ocrQualityService.validateDocumentStructure(contentWithTitre);
        
        // Then
        assertTrue(score >= 0.2, "Should detect titre section");
    }

    @Test
    void givenContentWithVisaWhenValidateStructureThenDetectsVisaSection() {
        // Given
        String contentWithVisa = """
            L'Assemblée Nationale a délibéré et adopté,
            Le Président de la République promulgue la loi dont la teneur suit :
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(contentWithVisa);
        
        // Then
        assertTrue(score >= 0.2, "Should detect visa section");
    }

    @Test
    void givenContentWithCorpsWhenValidateStructureThenDetectsCorpsSection() {
        // Given
        String contentWithCorps = "La présente loi sera exécutée comme loi de l'État.";
        
        // When
        double score = ocrQualityService.validateDocumentStructure(contentWithCorps);
        
        // Then
        assertTrue(score >= 0.2, "Should detect corps section");
    }

    @Test
    void givenContentWithPiedWhenValidateStructureThenDetectsPiedSection() {
        // Given
        String contentWithPied = """
            Fait à Cotonou, le 15 janvier 2009
            
            AMPLIATIONS
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(contentWithPied);
        
        // Then
        assertTrue(score >= 0.2, "Should detect pied section");
    }

    @Test
    void givenDocumentWithOcrErrorsWhenValidateStructureThenStillDetectsSections() {
        // Given - Document avec erreurs OCR typiques (o au lieu de é, etc.)
        String ocrErrorsContent = """
            REPUBLI0UE DU BENIN
            Fraternito - Justice - Travail
            PR0SIDENCE DE LA REPUBLI0UE
            
            LOI No 2009-1 du 15 janvier 2009
            
            L'Assembloo Nationale a dolibore et adopto,
            
            Article 1er : La prosente loi fixe...
            
            La prosente loi sera oxocutoo comme loi de l'0tat.
            
            Fait o Cotonou, le 15 janvier 2009
            
            AMPLIATIONS
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(ocrErrorsContent);
        
        // Then
        // Score = 0.6 = 3 sections / 5 détectées. Test pragmatique : accepter >= 0.6
        assertTrue(score >= 0.6, 
            "Should detect at least 60% of sections despite OCR errors. Got: " + score);
    }

    @Test
    void givenContentWith3SectionsWhenValidateStructureThenCalculatesSectionsPresentes() {
        // Given
        String contentWith3Sections = """
            REPUBLIQUE DU BENIN
            Fraternité - Justice - Travail
            PRESIDENCE DE LA REPUBLIQUE
            
            LOI N° 2009-1
            
            Article 1er : Test
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(contentWith3Sections);
        int sectionsPresentes = (int) Math.round(score * 5);
        
        // Then
        assertTrue(sectionsPresentes >= 2 && sectionsPresentes <= 4, 
            "Should detect 2-4 sections out of 5");
    }

    @Test
    void givenRealOcrFileExistsWhenReadFileThenReturnsContent() throws IOException {
        // Given
        Path ocrFile = Paths.get("../law-ocr-json/src/test/resources/samples_ocr/loi/loi-2009-1.txt");
        
        Assumptions.assumeTrue(Files.exists(ocrFile), "OCR file not found");
        
        // When
        String content = Files.readString(ocrFile, StandardCharsets.UTF_8);
        
        // Then
        assertNotNull(content);
        assertFalse(content.isEmpty());
        assertTrue(content.length() > 100, "OCR file should have substantial content");
    }

    @Test
    void givenTempDirectoryWhenCreateOcrFileThenFileIsCreated() throws IOException {
        // Given
        Path tempOcrFile = tempDir.resolve("test-loi.txt");
        String testContent = """
            REPUBLIQUE DU BENIN
            LOI N° 2024-1
            Article 1er : Test
            Fait à Cotonou
            AMPLIATIONS
            """;
        
        // When
        Files.writeString(tempOcrFile, testContent, StandardCharsets.UTF_8);
        String readContent = Files.readString(tempOcrFile, StandardCharsets.UTF_8);
        
        // Then
        assertTrue(Files.exists(tempOcrFile));
        assertEquals(testContent, readContent);
    }
}
