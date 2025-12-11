package bj.gouv.sgg.impl;

import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.exception.OcrExtractionException;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.DocumentMetadata;
import bj.gouv.sgg.model.Signatory;
import bj.gouv.sgg.service.OcrExtractionService;
import bj.gouv.sgg.service.UnrecognizedWordsService;
import bj.gouv.sgg.util.DateParsingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final UnrecognizedWordsService unrecognizedWordsService;
    
    @Override
    public List<Article> extractArticles(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new OcrExtractionException("OCR text is null or empty");
        }
        
        List<Article> articles = new ArrayList<>();
        
        try {
            String[] lines = text.split("\n");
            StringBuilder currentArticle = new StringBuilder();
            int index = 0;
            boolean inArticle = false;
            
            for (String line : lines) {
                boolean isStart = isArticleStart(line);
                
                if (isStart) {
                    Integer detectedNumber = extractArticleNumber(line);
                    
                    // Si premier article (index == 0), accepter n'importe quel numéro
                    // Sinon, vérifier la séquence attendue
                    boolean isValidSequence = (index == 0 && detectedNumber != null) || 
                                             (detectedNumber != null && detectedNumber == index + 1);
                    
                    if (isValidSequence) {
                        // Sauvegarder l'article précédent
                        if (inArticle && !currentArticle.isEmpty()) {
                            saveArticle(articles, index, currentArticle);
                            currentArticle.setLength(0);
                        }
                        
                        inArticle = true;
                        index++;
                        currentArticle.append(line).append("\n");
                        log.debug("📋 Article {} détecté (séquence valide)", detectedNumber);
                        
                    } else if (inArticle) {
                        // Sinon, si on est dans un article → article cité
                        currentArticle.append(line).append("\n");
                        if (detectedNumber != null) {
                            log.debug("📝 Article {} cité (inclus dans Article {})", detectedNumber, index);
                        }
                    }
                    
                } else if (inArticle) {
                    // Ligne normale d'un article
                    currentArticle.append(line).append("\n");
                }
            }
            
            // Dernier article
            if (inArticle && !currentArticle.isEmpty()) {
                saveArticle(articles, index, currentArticle);
            }
            
            if (articles.isEmpty()) {
                throw new OcrExtractionException("No articles found in OCR text (text length: " + text.length() + " chars)");
            }
            
            log.info("✅ Extracted {} articles via regex", articles.size());
            
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
     * Exemples:
     * - "Article 1er : ..." → 1
     * - "Article premier : ..." → 1
     * - "Article 2 : ..." → 2
     * - "Art. 3 : ..." → 3
     * - "ARTICLE 72 nouveau : ..." → 72
     */
    private Integer extractArticleNumber(String line) {
        // Pattern étendu pour capturer "premier" en plus de "1er"
        Pattern numberPattern = Pattern.compile("(?:Article|Art\\.)\\s+(?:(1er)|(premier)|(\\d+))", Pattern.CASE_INSENSITIVE);
        Matcher matcher = numberPattern.matcher(line);
        
        if (matcher.find()) {
            if (matcher.group(1) != null || matcher.group(2) != null) {
                return 1; // "1er" ou "premier"
            } else if (matcher.group(3) != null) {
                return Integer.parseInt(matcher.group(3));
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
        return calculateConfidence(text, articles, null);
    }
    
    /**
     * Calcule la confiance avec enregistrement optionnel des mots non reconnus
     * 
     * @param text Texte OCR
     * @param articles Articles extraits
     * @param documentId ID du document (null si pas d'enregistrement)
     * @return Score de confiance (0.0 - 1.0)
     */
    public double calculateConfidence(String text, List<Article> articles, String documentId) {
        if (text == null || text.isEmpty() || articles.isEmpty()) {
            return 0.0;
        }
        
        // Score basé sur le nombre d'articles
        double articleScore = Math.min(articles.size() / 10.0, 1.0);
        
        // Score basé sur la séquentialité des index d'articles
        double sequenceScore = calculateSequenceScore(articles);
        
        // Score basé sur la longueur du texte
        double textLengthScore = Math.min(text.length() / 5000.0, 1.0);
        
        // Analyse des mots non reconnus
        Set<String> unrecognizedWords = config.getUnrecognizedWords(text);
        double unrecRate = config.unrecognizedWordsRate(text);
        
        // Enregistrer les mots non reconnus si documentId fourni
        if (documentId != null && !unrecognizedWords.isEmpty()) {
            unrecognizedWordsService.recordUnrecognizedWords(unrecognizedWords, documentId);
        }
        
        // Score dictionnaire avec pénalité progressive
        double unrecPenalty = unrecognizedWordsService.calculateUnrecognizedPenalty(unrecRate, unrecognizedWords.size());
        double dictScore = 1.0 - unrecPenalty;
        
        // Score basé sur les termes juridiques
        int legalTerms = config.legalTermsFound(text);
        double legalScore = Math.min(legalTerms / 8.0, 1.0); // 8 termes max
        
        // Pondération: articles (20%), séquence (20%), longueur (15%), dictionnaire (25%), termes juridiques (20%)
        double confidence = (articleScore * 0.20) + (sequenceScore * 0.20) + (textLengthScore * 0.15) + (dictScore * 0.25) + (legalScore * 0.20);
        
        if (log.isDebugEnabled()) {
            log.debug("Confidence calculation: articles={}, sequence={}, text={}, dict={} (unrecRate={:.2f}%, penalty={:.2f}%), legal={} → total={}",
                    articleScore, sequenceScore, textLengthScore, dictScore, 
                    unrecRate * 100, unrecPenalty * 100, legalScore, confidence);
            
            if (!unrecognizedWords.isEmpty()) {
                log.debug("📝 Found {} unrecognized words (rate: {:.2f}%)", 
                        unrecognizedWords.size(), unrecRate * 100);
            }
        }
        
        return confidence;
    }
    
    /**
     * Calcule le score de séquentialité des index d'articles.
     * 
     * @param articles Liste des articles extraits
     * @return Score entre 0.0 (séquence très mauvaise) et 1.0 (séquence parfaite)
     */
    private double calculateSequenceScore(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return 0.0;
        }
        
        if (articles.size() == 1) {
            // Un seul article : séquence parfaite si index = 1
            return articles.get(0).getIndex() == 1 ? 1.0 : 0.8;
        }
        
        // Vérifier la séquentialité : chaque article doit avoir index = index_précédent + 1
        int gaps = 0;
        int duplicates = 0;
        int outOfOrder = 0;
        
        for (int i = 1; i < articles.size(); i++) {
            int prevIndex = articles.get(i - 1).getIndex();
            int currIndex = articles.get(i).getIndex();
            
            int diff = currIndex - prevIndex;
            
            if (diff > 1) {
                // Gap : articles manquants (ex: 1, 3, 4 → gap entre 1 et 3)
                gaps += (diff - 1);
            } else if (diff == 0) {
                // Duplicate : même index répété
                duplicates++;
            } else if (diff < 0) {
                // Out of order : ordre inversé (ex: 3, 2, 4)
                outOfOrder++;
            }
            // Si diff == 1 : séquence parfaite, pas de pénalité
        }
        
        int totalArticles = articles.size();
        
        // Pénalités
        double gapPenalty = (gaps * 0.15); // Chaque gap coûte 15%
        double duplicatePenalty = (duplicates * 0.25); // Chaque duplicate coûte 25%
        double outOfOrderPenalty = (outOfOrder * 0.30); // Chaque inversion coûte 30%
        
        double totalPenalty = gapPenalty + duplicatePenalty + outOfOrderPenalty;
        double score = Math.max(0.0, 1.0 - totalPenalty);
        
        if (gaps > 0 || duplicates > 0 || outOfOrder > 0) {
            log.debug("Sequence quality: {} articles, {} gaps, {} duplicates, {} out-of-order → score={}",
                     totalArticles, gaps, duplicates, outOfOrder, score);
        }
        
        return score;
    }
    
    private String formatDate(String day, String month, String year) {
        return DateParsingUtil.formatDate(day, month, year);
    }
    
    private boolean isArticleStart(String line) {
        return config.getArticleStart().matcher(line).find();
    }
    
    private void saveArticle(List<Article> articles, int index, StringBuilder currentArticle) {
        String content = currentArticle.toString().trim();
        if (content.length() > 10) {
            articles.add(Article.builder()
                .index(index)
                .content(content)
                .build());
            log.debug("Article {} extracted: {} chars", index, content.length());
        }
    }
}
