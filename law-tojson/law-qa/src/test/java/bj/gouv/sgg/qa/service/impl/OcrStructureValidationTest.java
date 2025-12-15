package bj.gouv.sgg.qa.service.impl;

import bj.gouv.sgg.qa.service.OcrQualityService;
import bj.gouv.sgg.qa.service.UnrecognizedWordsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests pour la validation de structure OCR d'un document de loi.
 * 
 * Utilise Spring Boot pour charger les patterns depuis ocr-validation.properties
 */
@SpringBootTest(classes = {OcrQualityServiceImpl.class})
@TestPropertySource(locations = "classpath:ocr-validation.properties")
class OcrStructureValidationTest {
    
    @MockBean
    private UnrecognizedWordsService unrecognizedWordsService;
    
    @Autowired
    private OcrQualityService ocrQualityService;
    
    @Test
    void shouldDetectCompleteStructure() {
        // Given : Document complet avec les 5 sections
        String completeDocument = """
            REPUBLIQUE DU BENIN
            Fraternité-Justice-Travail
            PRESIDENCE DE LA REPUBLIQUE
            
            LOI N° 2009-01 DU 16 JANVIER 2009
            portant autorisation de ratification de l'Accord
            
            L'Assemblée nationale a délibéré et adopté en sa séance du 12 janvier 2009,
            Le Président de la République promulgue la Loi dont la teneur suit:
            
            Article 1er: Est autorisée, la ratification...
            
            Article 2 : La présente Loi sera exécutée comme Loi de l'Etat.
            
            Fait à Cotonou, le 16 janvier 2009
            Par le Président de la République,
            Dr Boni YAYI
            
            AMPLIATIONS: PR 6, AN 4
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(completeDocument);
        
        // Then : Toutes les 5 sections présentes = 1.0
        assertThat(score).isEqualTo(1.0);
    }
    
    @Test
    void shouldDetectMissingHeader() {
        // Given : Document sans entête complet
        String documentWithoutHeader = """
            LOI N° 2009-01 DU 16 JANVIER 2009
            
            L'Assemblée nationale a délibéré et adopté en sa séance du 12 janvier 2009,
            
            Article 1er: Est autorisée...
            Article 2 : La présente Loi sera exécutée comme Loi de l'Etat.
            
            Fait à Cotonou, le 16 janvier 2009
            AMPLIATIONS: PR 6
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(documentWithoutHeader);
        
        // Then : 4/5 sections = 0.8
        assertThat(score).isEqualTo(0.8);
    }
    
    @Test
    void shouldDetectMissingTitle() {
        // Given : Document sans titre LOI N°
        String documentWithoutTitle = """
            REPUBLIQUE DU BENIN
            Fraternité-Justice-Travail
            PRESIDENCE DE LA REPUBLIQUE
            
            L'Assemblée nationale a délibéré et adopté...
            
            Article 1er: Est autorisée...
            Article 2 : La présente Loi sera exécutée comme Loi de l'Etat.
            
            Fait à Cotonou, le 16 janvier 2009
            AMPLIATIONS
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(documentWithoutTitle);
        
        // Then : 4/5 sections = 0.8
        assertThat(score).isEqualTo(0.8);
    }
    
    @Test
    void shouldDetectMissingVisa() {
        // Given : Document sans visa
        String documentWithoutVisa = """
            REPUBLIQUE DU BENIN
            Fraternité-Justice-Travail
            PRESIDENCE DE LA REPUBLIQUE
            
            LOI N° 2009-01 DU 16 JANVIER 2009
            
            Article 1er: Est autorisée...
            Article 2 : La présente Loi sera exécutée comme Loi de l'Etat.
            
            Fait à Cotonou, le 16 janvier 2009
            AMPLIATIONS
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(documentWithoutVisa);
        
        // Then : 4/5 sections = 0.8
        assertThat(score).isEqualTo(0.8);
    }
    
    @Test
    void shouldDetectMissingCorpsEnd() {
        // Given : Document sans formule de fin du corps
        String documentWithoutCorpsEnd = """
            REPUBLIQUE DU BENIN
            Fraternité-Justice-Travail
            PRESIDENCE DE LA REPUBLIQUE
            
            LOI N° 2009-01 DU 16 JANVIER 2009
            
            L'Assemblée nationale a délibéré et adopté...
            
            Article 1er: Est autorisée...
            
            Fait à Cotonou, le 16 janvier 2009
            AMPLIATIONS
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(documentWithoutCorpsEnd);
        
        // Then : 4/5 sections = 0.8
        assertThat(score).isEqualTo(0.8);
    }
    
    @Test
    void shouldDetectMissingPied() {
        // Given : Document sans pied (Fait à... AMPLIATIONS)
        String documentWithoutPied = """
            REPUBLIQUE DU BENIN
            Fraternité-Justice-Travail
            PRESIDENCE DE LA REPUBLIQUE
            
            LOI N° 2009-01 DU 16 JANVIER 2009
            
            L'Assemblée nationale a délibéré et adopté...
            
            Article 1er: Est autorisée...
            Article 2 : La présente Loi sera exécutée comme Loi de l'Etat.
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(documentWithoutPied);
        
        // Then : 4/5 sections = 0.8
        assertThat(score).isEqualTo(0.8);
    }
    
    @Test
    void shouldHandleEmptyText() {
        // When
        double score = ocrQualityService.validateDocumentStructure("");
        
        // Then
        assertThat(score).isEqualTo(0.0);
    }
    
    @Test
    void shouldHandleNullText() {
        // When
        double score = ocrQualityService.validateDocumentStructure(null);
        
        // Then
        assertThat(score).isEqualTo(0.0);
    }
    
    @Test
    void shouldHandlePartialHeader() {
        // Given : Entête avec seulement REPUBLIQUE (sans devise ni PRESIDENCE)
        String documentWithPartialHeader = """
            REPUBLIQUE DU BENIN
            
            LOI N° 2009-01 DU 16 JANVIER 2009
            L'Assemblée nationale a délibéré et adopté...
            Article 1er: Est autorisée...
            Article 2 : La présente Loi sera exécutée comme Loi de l'Etat.
            Fait à Cotonou, le 16 janvier 2009
            AMPLIATIONS
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(documentWithPartialHeader);
        
        // Then : Entête incomplet compté comme absent → 4/5 = 0.8
        assertThat(score).isEqualTo(0.8);
    }
    
    @Test
    void shouldDetectAlternativeCorpsEnd() {
        // Given : Document avec formule alternative "abroge toutes dispositions..."
        String documentWithAbrogeFormula = """
            REPUBLIQUE DU BENIN
            Fraternité-Justice-Travail
            PRESIDENCE DE LA REPUBLIQUE
            
            LOI N° 2009-01 DU 16 JANVIER 2009
            
            L'Assemblée nationale a délibéré et adopté...
            
            Article 1er: Est autorisée...
            Article 2 : La présente loi abroge toutes dispositions antérieures contraires.
            
            Fait à Cotonou, le 16 janvier 2009
            AMPLIATIONS
            """;
        
        // When
        double score = ocrQualityService.validateDocumentStructure(documentWithAbrogeFormula);
        
        // Then : Toutes sections présentes = 1.0
        assertThat(score).isEqualTo(1.0);
    }
}
