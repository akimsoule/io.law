package bj.gouv.sgg.config;

import bj.gouv.sgg.model.Signatory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour ArticleExtractorConfig
 */
class ArticleExtractorConfigTest {

    private ArticleExtractorConfig config;

    @BeforeEach
    void setUp() {
        config = new ArticleExtractorConfig();
        config.init();
    }

    @Test
    void givenConfigInitializedWhenGetPropsThenReturnsLoadedProperties() {
        Properties props = config.getProps();
        
        assertNotNull(props);
        assertFalse(props.isEmpty());
        assertTrue(props.size() >= 10, "Au moins 10 patterns devraient être chargés");
        
        // Vérifier quelques propriétés clés
        assertNotNull(props.getProperty("article.start"));
        assertNotNull(props.getProperty("article.end.any"));
        assertNotNull(props.getProperty("lawTitle.start"));
        assertNotNull(props.getProperty("lawTitle.end"));
    }

    @Test
    void givenConfigInitializedWhenGetSignatoryPatternsThenReturnsCompiledPatterns() {
        Map<Pattern, Signatory> patterns = config.getSignatoryPatterns();
        
        assertNotNull(patterns);
        assertFalse(patterns.isEmpty());
        assertTrue(patterns.size() >= 3, "Au moins 3 signataires devraient être chargés");
        
        // Vérifier qu'on peut matcher un signataire connu
        boolean foundPatrice = patterns.keySet().stream()
            .anyMatch(p -> p.pattern().contains("Patrice") || p.pattern().contains("TALON"));
        
        assertTrue(foundPatrice, "Le pattern pour Patrice TALON devrait exister");
    }

    @Test
    void givenConfigInitializedWhenGetFrenchDictThenReturnsLargeDictionary() {
        Set<String> dict = config.getFrenchDict();
        
        assertNotNull(dict);
        assertFalse(dict.isEmpty());
        assertTrue(dict.size() > 100000, "Le dictionnaire devrait contenir plus de 100k mots");
        
        // Vérifier quelques mots français courants
        assertTrue(dict.contains("article"));
        assertTrue(dict.contains("loi"));
        assertTrue(dict.contains("république"));
        assertTrue(dict.contains("président"));
    }

    @Test
    void givenConfigInitializedWhenGetPatternsThenAllPatternsCompiled() {
        // Vérifier que tous les patterns sont compilés
        assertNotNull(config.getArticleStart());
        assertNotNull(config.getArticleEndAny());
        assertNotNull(config.getLawTitleStart());
        assertNotNull(config.getLawTitleEnd());
        assertNotNull(config.getLawEndStart());
        assertNotNull(config.getLawEndEnd());
        assertNotNull(config.getPromulgationCity());
        assertNotNull(config.getPromulgationDate());
    }

    @Test
    void givenArticleStartPatternWhenMatchDifferentFormatsThenFindsAllVariants() {
        Pattern articleStart = config.getArticleStart();
        
        // Doit matcher différents formats d'articles
        assertTrue(articleStart.matcher("Article 1").find());
        assertTrue(articleStart.matcher("ARTICLE 2").find());
        assertTrue(articleStart.matcher("Art. 3").find());
        assertTrue(articleStart.matcher("  Article 1er").find());
    }

    @Test
    void givenLawTitleStartPatternWhenMatchDifferentFormatsThenFindsAllVariants() {
        Pattern lawTitleStart = config.getLawTitleStart();
        
        // Doit matcher différents formats de titres de loi
        assertTrue(lawTitleStart.matcher("loi n° 2024-15").find());
        assertTrue(lawTitleStart.matcher("LOI N° 2024-15").find());
        assertTrue(lawTitleStart.matcher("Loi no 2024-15").find());
    }

    @Test
    void givenFrenchTextWhenUnrecognizedWordsRateThenReturnsLowRate() {
        // Texte avec mots français reconnus
        String frenchText = "Article premier de la loi portant modification du code pénal";
        double rate1 = config.unrecognizedWordsRate(frenchText);
        assertTrue(rate1 < 0.3, "Taux de mots non reconnus devrait être faible pour texte français");
        
        // Texte avec beaucoup de mots inconnus
        String gibberish = "xyzabc qwerty asdfgh zxcvbn poiuyt lkjhgf";
        double rate2 = config.unrecognizedWordsRate(gibberish);
        assertTrue(rate2 > 0.7, "Taux de mots non reconnus devrait être élevé pour charabia");
    }

    @Test
    void givenEmptyOrNullTextWhenUnrecognizedWordsRateThenReturnsZero() {
        assertEquals(0.0, config.unrecognizedWordsRate(""));
        assertEquals(0.0, config.unrecognizedWordsRate(null));
    }

    @Test
    void givenShortWordsWhenUnrecognizedWordsRateThenIgnoresShortWords() {
        // Les mots de moins de 3 caractères sont ignorés
        String shortWords = "a de la le un";
        double rate = config.unrecognizedWordsRate(shortWords);
        assertEquals(0.0, rate, "Les mots courts devraient être ignorés");
    }

    @Test
    void givenLegalTextWhenLegalTermsFoundThenReturnsHighCount() {
        String legalText = "Article premier de la loi portant modification. " +
                          "Conformément aux dispositions de la République, " +
                          "le Président promulgue le décret publié au Journal Officiel.";
        
        int found = config.legalTermsFound(legalText);
        
        assertTrue(found >= 8, "Au moins 8 termes juridiques devraient être trouvés");
    }

    @Test
    void givenEmptyOrNullTextWhenLegalTermsFoundThenReturnsZero() {
        assertEquals(0, config.legalTermsFound(""));
        assertEquals(0, config.legalTermsFound(null));
    }

    @Test
    void givenNonLegalTextWhenLegalTermsFoundThenReturnsZero() {
        String nonLegalText = "The quick brown fox jumps over the lazy dog";
        int found = config.legalTermsFound(nonLegalText);
        
        assertEquals(0, found, "Aucun terme juridique ne devrait être trouvé");
    }

    @Test
    void givenPromulgationDatePatternWhenMatchDifferentFormatsThenFindsAllVariants() {
        Pattern datePattern = config.getPromulgationDate();
        
        // Doit matcher différents formats de dates
        assertTrue(datePattern.matcher("15 décembre 2024").find());
        assertTrue(datePattern.matcher("1er janvier 2024").find());
        assertTrue(datePattern.matcher("3ème mars 2023").find());
        assertTrue(datePattern.matcher("le 25 juin 2022").find());
    }

    @Test
    void givenPromulgationCityPatternWhenMatchDifferentCitiesThenFindsAllVariants() {
        Pattern cityPattern = config.getPromulgationCity();
        
        // Doit matcher les villes de promulgation
        assertTrue(cityPattern.matcher("Fait à Cotonou").find());
        assertTrue(cityPattern.matcher("Fait à Porto-Novo").find());
        assertTrue(cityPattern.matcher("Fait à Abomey-Calavi").find());
    }

    @Test
    void givenSignatureTextWhenMatchPatternsThenFindsCorrectSignatory() {
        Map<Pattern, Signatory> patterns = config.getSignatoryPatterns();
        
        String signatureText = "Le Président de la République Patrice TALON";
        
        boolean matched = false;
        Signatory foundSignatory = null;
        
        for (Map.Entry<Pattern, Signatory> entry : patterns.entrySet()) {
            if (entry.getKey().matcher(signatureText).find()) {
                matched = true;
                foundSignatory = entry.getValue();
                break;
            }
        }
        
        assertTrue(matched, "Le pattern devrait matcher Patrice TALON");
        assertNotNull(foundSignatory);
        assertTrue(foundSignatory.getName().contains("TALON"));
    }
}
