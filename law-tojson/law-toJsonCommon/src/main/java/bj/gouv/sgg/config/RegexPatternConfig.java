package bj.gouv.sgg.config;

import bj.gouv.sgg.util.PatternCache;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Configuration des patterns regex utilisés dans l'application
 * TOUS les patterns sont chargés depuis patterns.properties
 * et mis en cache via PatternCache pour optimiser les performances
 */
@Slf4j
@Component
public class RegexPatternConfig {
    
    private final Properties props = new Properties();
    
    // Patterns chargés depuis patterns.properties
    @Getter
    private Pattern articleStart;
    @Getter
    private Pattern articleEndAny;
    @Getter
    private Pattern lawTitleStart;
    @Getter
    private Pattern lawTitleEnd;
    @Getter
    private Pattern promulgationCity;
    @Getter
    private Pattern promulgationDate;
    @Getter
    private Pattern documentIdPattern;
    @Getter
    private Pattern multipleSpaces;
    @Getter
    private Pattern multipleNewlines;
    @Getter
    private Pattern controlChars;
    @Getter
    private Pattern emailPattern;
    @Getter
    private Pattern yearPattern;
    @Getter
    private String wordBoundaryTemplate;
    
    @PostConstruct
    public void init() {
        loadProperties();
        compilePatterns();
        log.info("RegexPatternConfig initialized with {} patterns from patterns.properties", PatternCache.getCacheSize());
    }
    
    private void loadProperties() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("patterns.properties")) {
            if (is == null) {
                throw new RuntimeException("Cannot find patterns.properties in classpath");
            }
            props.load(is);
            log.info("Loaded {} properties from patterns.properties", props.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load patterns.properties", e);
        }
    }
    
    private void compilePatterns() {
        // Patterns pour extraction d'articles
        articleStart = compileFromProperty("article.start", Pattern.MULTILINE);
        articleEndAny = compileFromProperty("article.end.any", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        
        // Patterns pour métadonnées
        lawTitleStart = compileFromProperty("lawTitle.start", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        lawTitleEnd = compileFromProperty("lawTitle.end", Pattern.MULTILINE);
        promulgationCity = compileFromProperty("promulgation.city", Pattern.MULTILINE);
        promulgationDate = compileFromProperty("promulgation.date", Pattern.CASE_INSENSITIVE);
        
        // Patterns pour identifiants
        documentIdPattern = compileFromProperty("document.id", 0);
        
        // Patterns pour nettoyage texte
        multipleSpaces = compileFromProperty("multiple.spaces", 0);
        multipleNewlines = compileFromProperty("multiple.newlines", 0);
        controlChars = compileFromProperty("control.chars", 0);
        
        // Patterns pour validation
        emailPattern = compileFromProperty("email.pattern", 0);
        yearPattern = compileFromProperty("year.pattern", 0);
        
        // Template pour word boundary
        wordBoundaryTemplate = props.getProperty("word.boundary");
    }
    
    private Pattern compileFromProperty(String key, int flags) {
        String regex = props.getProperty(key);
        if (regex == null) {
            throw new RuntimeException("Missing pattern property: " + key);
        }
        return PatternCache.get(regex, flags);
    }
    
    // ============= CORRECTIONS OCR =============
    
    /**
     * Crée un pattern pour remplacer un mot spécifique avec boundaries
     * Utilise le template word.boundary depuis patterns.properties
     * Utilise le cache pour éviter les recompilations
     */
    public Pattern createWordBoundaryPattern(String word) {
        String regex = String.format(wordBoundaryTemplate, Pattern.quote(word));
        return PatternCache.get(regex, 0);
    }
    
    // ============= NETTOYAGE TEXTE =============
    
    /**
     * Nettoie les espaces multiples dans un texte
     */
    public String cleanMultipleSpaces(String text) {
        return multipleSpaces.matcher(text).replaceAll(" ");
    }
    
    /**
     * Nettoie les lignes vides multiples dans un texte
     */
    public String cleanMultipleNewlines(String text) {
        return multipleNewlines.matcher(text).replaceAll("\n\n");
    }
    
    /**
     * Supprime les caractères de contrôle (sauf \n, \r, \t)
     */
    public String removeControlChars(String text) {
        return controlChars.matcher(text).replaceAll("");
    }
    
    /**
     * Nettoie un texte OCR (espaces, newlines, caractères de contrôle)
     */
    public String cleanOcrText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        text = removeControlChars(text);
        text = cleanMultipleSpaces(text);
        text = cleanMultipleNewlines(text);
        return text.trim();
    }
}
