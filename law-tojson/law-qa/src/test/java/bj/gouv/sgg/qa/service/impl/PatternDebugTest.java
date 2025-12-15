package bj.gouv.sgg.qa.service.impl;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de debug pour vérifier que les patterns matchent correctement
 */
class PatternDebugTest {
    
    @Test
    void testDevisePatternDirect() {
        // Pattern direct en Java
        String patternString = "Fraternit[ée]\\s*-?\\s*Justice\\s*-?\\s*Travail";
        Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        
        String text = "Fraternité-Justice-Travail";
        
        boolean matches = pattern.matcher(text).find();
        assertThat(matches).isTrue();
    }
    
    @Test
    void testRepubliquePattern() {
        String patternString = "R[ÉE]PUBLI(?:QUE|OUE)\\s+DU\\s+B[ÉE]NIN";
        Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        
        String text = "REPUBLIQUE DU BENIN";
        
        boolean matches = pattern.matcher(text).find();
        assertThat(matches).isTrue();
    }
    
    @Test
    void testVisaPattern() {
        String patternString = "L['']assembl[ée]e\\s+nationale\\s+a\\s+d[ée]lib[ée]r[ée]";
        Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        
        String text = "L'Assemblée nationale a délibéré et adopté";
        
        boolean matches = pattern.matcher(text).find();
        assertThat(matches).isTrue();
    }
    
    @Test
    void testCorpsFinPattern() {
        String patternString = "(sera\\s+ex[ée]cut[ée]e\\s+comme\\s+loi\\s+de\\s+l['']?[ÉE]tat)|(abroge\\s+toutes\\s+dispositions\\s+ant[ée]rieures\\s+contraires)";
        Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        
        String text = "La présente Loi sera exécutée comme Loi de l'Etat.";
        
        boolean matches = pattern.matcher(text).find();
        assertThat(matches).isTrue();
    }
    
    @Test
    void testPiedDebutPattern() {
        String patternString = "Fait\\s+[àa]\\s+\\w+";
        Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        
        String text = "Fait à Cotonou, le 16 janvier 2009";
        
        boolean matches = pattern.matcher(text).find();
        assertThat(matches).isTrue();
    }
}
