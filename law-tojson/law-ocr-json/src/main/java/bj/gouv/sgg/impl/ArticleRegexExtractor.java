package bj.gouv.sgg.impl;

import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.exception.OcrExtractionException;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.DocumentMetadata;
import bj.gouv.sgg.model.Signatory;
import bj.gouv.sgg.service.OcrExtractionService;
import bj.gouv.sgg.util.DateParsingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implémentation regex-based de l'extraction OCR
 * Utilise ArticleExtractorConfig pour charger les patterns depuis patterns.properties
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleRegexExtractor implements OcrExtractionService {
    
    private final ArticleExtractorConfig config;
    
    @Override
    public List<Article> extractArticles(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new OcrExtractionException("OCR text is null or empty");
        }
        
        List<Article> articles = new ArrayList<>();
        
        try {
            String[] lines = text.split("\n");
            StringBuilder currentArticle = new StringBuilder();
            int expectedNumber = 1; // Numéro attendu : 1, 2, 3...
            boolean inArticle = false;
            
            for (String line : lines) {
                boolean isStart = isArticleStart(line);
                boolean isEnd = isArticleEnd(line);
                
                // Si on détecte un début/fin ET qu'on est déjà dans un article, on sauve l'article précédent
                if ((isStart || isEnd) && inArticle && !currentArticle.isEmpty()) {
                    saveArticleIfValid(articles, expectedNumber, currentArticle);
                    currentArticle.setLength(0);
                    inArticle = false;
                }
                
                // Début d'un nouvel article
                if (isStart) {
                    // Extraire le numéro de l'article détecté
                    Integer detectedNumber = extractArticleNumber(line);
                    
                    if (detectedNumber != null && detectedNumber == expectedNumber) {
                        // ✅ Numéro cohérent avec la séquence
                        inArticle = true;
                        expectedNumber++; // Préparer pour le prochain
                        log.debug("📋 Article {} détecté (séquence valide)", detectedNumber);
                    } else if (detectedNumber != null) {
                        // ⚠️ Numéro incohérent → probablement un article cité
                        log.debug("⏭️ Article {} ignoré (attendu: {})", detectedNumber, expectedNumber);
                        inArticle = false;
                        continue;
                    } else {
                        // Pattern détecté mais numéro non extrait → on accepte par défaut
                        inArticle = true;
                        expectedNumber++;
                    }
                }
                
                // Accumuler les lignes de l'article courant
                if (inArticle) {
                    currentArticle.append(line).append("\n");
                }
            }
            
            // Dernier article
            if (inArticle && !currentArticle.isEmpty()) {
                saveArticleIfValid(articles, expectedNumber, currentArticle);
            }
            
            if (articles.isEmpty()) {
                throw new OcrExtractionException("No articles found in OCR text (text length: " + text.length() + " chars)");
            }
            
            log.info("✅ Extracted {} articles via regex with sequence validation", articles.size());
            
        } catch (OcrExtractionException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Error extracting articles: {}", e.getMessage());
            throw new OcrExtractionException("Failed to extract articles from OCR text", e);
        }
        
        return articles;
    }
    
    /**
     * Extrait le numéro d'un article depuis une ligne.
     * Retourne null si non trouvé.
     * 
     * Exemples:
     * - "Article 1er : ..." → 1
     * - "Article 2: ..." → 2
     * - "Article 72: ..." → 72
     */
    private Integer extractArticleNumber(String line) {
        // Pattern pour extraire le numéro
        Pattern numberPattern = Pattern.compile("Article\\s+(?:(1er)|(\\d+))", Pattern.CASE_INSENSITIVE);
        Matcher matcher = numberPattern.matcher(line);
        
        if (matcher.find()) {
            if (matcher.group(1) != null) {
                return 1; // "1er"
            } else if (matcher.group(2) != null) {
                return Integer.parseInt(matcher.group(2)); // Chiffre
            }
        }
        
        return null;
    }
    
    @Override
    public DocumentMetadata extractMetadata(String text) {
        DocumentMetadata metadata = DocumentMetadata.builder().build();
        
        // Extract law title using config patterns
        Matcher titleStartMatcher = config.getLawTitleStart().matcher(text);
        if (titleStartMatcher.find()) {
            int start = titleStartMatcher.start();
            Matcher titleEndMatcher = config.getLawTitleEnd().matcher(text.substring(start));
            if (titleEndMatcher.find()) {
                String title = text.substring(start, start + titleEndMatcher.start()).trim();
                metadata.setLawTitle(title);
                log.debug("Extracted title: {}", title);
            }
        }
        
        // Extract date using config pattern
        Matcher dateMatcher = config.getPromulgationDate().matcher(text);
        if (dateMatcher.find()) {
            String day = dateMatcher.group(1);
            String month = dateMatcher.group(3);
            String year = dateMatcher.group(4);
            metadata.setPromulgationDate(formatDate(day, month, year));
            log.debug("Extracted date: {}", metadata.getPromulgationDate());
        }
        
        // Extract city using config pattern
        Matcher cityMatcher = config.getPromulgationCity().matcher(text);
        if (cityMatcher.find()) {
            metadata.setPromulgationCity(cityMatcher.group(1).trim());
            log.debug("Extracted city: {}", metadata.getPromulgationCity());
        }
        
        // Extract signatories using config patterns
        List<Signatory> signatories = new ArrayList<>();
        for (Map.Entry<Pattern, Signatory> entry : config.getSignatoryPatterns().entrySet()) {
            Matcher matcher = entry.getKey().matcher(text);
            if (matcher.find()) {
                signatories.add(entry.getValue());
            }
        }
        metadata.setSignatories(signatories);
        log.debug("Extracted {} signatories", signatories.size());
        
        return metadata;
    }
    
    @Override
    public double calculateConfidence(String text, List<Article> articles) {
        if (text == null || text.isEmpty() || articles.isEmpty()) {
            return 0.0;
        }
        
        // Score basé sur le nombre d'articles
        double articleScore = Math.min(articles.size() / 10.0, 1.0);
        
        // Score basé sur la longueur du texte
        double textLengthScore = Math.min(text.length() / 5000.0, 1.0);
        
        // Score basé sur la qualité OCR (dictionnaire français)
        double unrec = config.unrecognizedWordsRate(text);
        double dictScore = 1.0 - unrec; // Plus de mots reconnus = meilleur score
        
        // Score basé sur les termes juridiques
        int legalTerms = config.legalTermsFound(text);
        double legalScore = Math.min(legalTerms / 8.0, 1.0); // 8 termes max
        
        // Pondération: articles (30%), longueur (20%), dictionnaire (30%), termes juridiques (20%)
        double confidence = (articleScore * 0.3) + (textLengthScore * 0.2) + (dictScore * 0.3) + (legalScore * 0.2);
        
        log.debug("Confidence calculation: articles={}, text={}, dict={}, legal={} → total={}", 
                  articleScore, textLengthScore, dictScore, legalScore, confidence);
        
        return confidence;
    }
    
    private String formatDate(String day, String month, String year) {
        return DateParsingUtil.formatDate(day, month, year);
    }
    
    private boolean isArticleStart(String line) {
        return config.getArticleStart().matcher(line).find();
    }
    
    private boolean isArticleEnd(String line) {
        return config.getArticleEndAny().matcher(line).find();
    }
    
    private void saveArticleIfValid(List<Article> articles, int expectedNumber, StringBuilder currentArticle) {
        String content = currentArticle.toString().trim();
        if (content.length() > 10) {
            int index = articles.size() + 1;
            articles.add(Article.builder()
                .index(index)
                .content(content)
                .build());
            log.debug("✅ Article {} saved: {} chars (expected: {})", index, content.length(), expectedNumber);
        }
    }
}
