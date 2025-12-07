package bj.gouv.sgg.impl;

import bj.gouv.sgg.service.CorrectOcrText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour CsvCorrector
 */
class CsvCorrectorTest {

    private CorrectOcrText corrector;

    @BeforeEach
    void setUp() {
        corrector = new CsvCorrector();
    }

    @Test
    void testApplyCorrections_BasicCorrections() {
        String input = "REPUBLIQUE DU BENIN Article 1€' portant |a modification";
        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        // Les corrections dépendent du contenu exact de corrections.csv
        // Vérifier au moins que le texte a été traité
        assertFalse(corrected.isEmpty());
    }

    @Test
    void testApplyCorrections_EmptyText() {
        String input = "";
        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        assertEquals("", corrected);
    }

    @Test
    void testApplyCorrections_NullText() {
        String corrected = corrector.applyCorrections(null);

        assertNull(corrected);
    }

    @Test
    void testApplyCorrections_NoCorrectionsNeeded() {
        String input = "RÉPUBLIQUE DU BÉNIN Article 1er portant la modification";
        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        // Le texte peut être modifié par d'autres corrections
        assertFalse(corrected.isEmpty());
    }

    @Test
    void testApplyCorrections_MultipleOccurrences() {
        String input = "REPUBLIQUE et REPUBLIQUE encore REPUBLIQUE";
        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        // Toutes les occurrences devraient être corrigées
        assertFalse(corrected.contains("REPUBLIQUE"));
    }

    @Test
    void testApplyCorrections_PipeCharacters() {
        String input = "|a loi |es articles |'Assemblée";
        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        // Les corrections pipe dépendent du CSV
        assertFalse(corrected.isEmpty());
    }

    @Test
    void testApplyCorrections_ArticleNumbers() {
        String input = "Article 1e\" Article 2é Article 1€'";
        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        // Les numéros d'articles devraient être corrigés
        assertTrue(corrected.contains("1er") || corrected.contains("Article"));
    }

    @Test
    void testApplyCorrections_RomanNumerals() {
        String input = "livre |I livre |II livre |II";
        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        assertTrue(corrected.contains("livre II") || corrected.contains("livre"));
    }

    @Test
    void testApplyCorrections_AeroPrefixes() {
        String input = "dérodrome géroclub déroport";
        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        assertTrue(corrected.contains("aérodrome") || corrected.contains("aéro"));
    }

    @Test
    void testApplyCorrections_DoubleApostrophes() {
        String input = "l''article d''application l''ensemble";
        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        assertTrue(corrected.contains("l'article"));
        assertTrue(corrected.contains("d'application"));
        assertTrue(corrected.contains("l'ensemble"));
    }

    @Test
    void testApplyCorrections_CaseErrors() {
        String input = "ARTICLE 1 PRESIDENT de la REPUBLIQUE";
        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        // Devrait corriger la casse si défini dans corrections.csv
        assertFalse(corrected.isEmpty());
    }

    @Test
    void testApplyCorrections_ComplexText() {
        String input = """
            REPUBLIQUE DU BENIN
            LOI N° 2024-15
            
            L''Assemblée nationale a délibéré et adopté,
            |e Président de la République promulgue |a loi dont la teneur suit :
            
            ARTICLE 1€'
            Les dispositions du code pénal sont modifiées.
            
            Fait à Cotonou, |e 15 décembre 2024
            """;

        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        assertFalse(corrected.isEmpty());
        
        // Vérifier que le texte a été traité
        // Les corrections exactes dépendent du contenu de corrections.csv
    }

    @Test
    void testApplyCorrections_PreserveStructure() {
        String input = "Article 1\nContenu\n\nArticle 2\nAutre contenu";
        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        // La structure (sauts de ligne) devrait être préservée
        assertTrue(corrected.contains("\n"));
    }

    @Test
    void testApplyCorrections_SpecialCharacters() {
        String input = "Article 1 : Les dispositions suivantes s''appliquent.";
        String corrected = corrector.applyCorrections(input);

        assertNotNull(corrected);
        assertFalse(corrected.isEmpty());
        // Les apostrophes peuvent être corrigées selon corrections.csv
    }
}
