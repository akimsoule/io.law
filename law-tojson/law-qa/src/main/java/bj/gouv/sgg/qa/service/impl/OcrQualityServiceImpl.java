package bj.gouv.sgg.qa.service.impl;

import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.qa.service.OcrQualityService;
import bj.gouv.sgg.qa.service.UnrecognizedWordsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@PropertySource("classpath:ocr-validation.properties")
public class OcrQualityServiceImpl implements OcrQualityService {
    private final UnrecognizedWordsService unrecognizedWordsService;

    // Patterns compilés
    private Pattern headerRepubliquePattern;
    private Pattern headerDevisePattern;
    private Pattern headerPresidencePattern;
    private Pattern titleLoiPattern;
    private Pattern visaAssembleePattern;
    
    private Pattern visaPromulgationPattern;
    private Pattern visaArticle1Pattern;
    private Pattern corpsFinPattern;
    private Pattern articlePattern;
    private Pattern piedDebutPattern;
    private Pattern piedFinPattern;

    // Dictionnaire français
    private Set<String> frenchDictionary = Collections.emptySet();

    // Termes juridiques chargés depuis propriétés
    private Set<String> legalTerms;

    // Initialisation paresseuse pour les tests sans contexte Spring
    private volatile boolean initialized = false;

    // Valeurs depuis ocr-validation.properties
    @Value("${header.republique}")
    private String patternHeaderRepublique;
    @Value("${header.devise}")
    private String patternHeaderDevise;
    @Value("${header.presidence}")
    private String patternHeaderPresidence;
    @Value("${title.loi}")
    private String patternTitleLoi;
    @Value("${visa.assemblee}")
    private String patternVisaAssemblee;
    @Value("${visa.promulgation}")
    private String patternVisaPromulgation;
    @Value("${visa.article1}")
    private String patternVisaArticle1;
    @Value("${corps.fin}")
    private String patternCorpsFin;
    @Value("${article.pattern}")
    private String patternArticle;
    @Value("${pied.debut}")
    private String patternPiedDebut;
    @Value("${pied.fin}")
    private String patternPiedFin;
    @Value("${legal.terms}")
    private String legalTermsConfig;
    //
    
    
    /**
     * Initialise les patterns et le dictionnaire au démarrage
     */
    @PostConstruct
    private void initialize() {
        // Debug : log des patterns reçus
        log.debug("🔍 Pattern header.republique : [{}]", patternHeaderRepublique);
        log.debug("🔍 Pattern header.devise : [{}]", patternHeaderDevise);
        log.debug("🔍 Pattern visa.assemblee : [{}]", patternVisaAssemblee);
        log.debug("🔍 Pattern corps.fin : [{}]", patternCorpsFin);
        log.debug("🔍 Pattern pied.debut : [{}]", patternPiedDebut);
        
        // Compilation des patterns avec flags appropriés (défensif pour tests sans propriétés)
        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        
        // Patterns TRÈS tolérants aux erreurs OCR (substitutions courantes: 0↔O↔Q, I↔l↔L↔1, É↔E, oo↔ee, etc.)
        String republiqueTolerante = "R[EÉEeè][PpI][UuV][BbI][LlI][IlL1][QqO0][UuV][EÉEeè]";
        String beninTolerant = "B[EÉEeè][NnIl][IlL1][NnIl]";
        String presidenceTolerante = "PR[EÉE0Oeèo][SsI][IlL1][DdO0][EÉEeèo][NnIl][CcG][EÉEeè]";
        
        // Assemblée/Assemblee avec tolérance: Ass[eo]mbl[eo][eo] (accepte oo, ee, eo)
        String assembleeTolerante = "[AaOo0][SsI][SsI][EÉEeèo][MmIl][BbI][LlI][EÉEeèo][EÉEeèo]";
        
        // Delibere/dolibore avec tolérance: d[eo]lib[eo]r[eo]
        String delibereTolerante = "[DdOo0][EÉEeèo][LlI][IlL1][BbI][EÉEeèo][RrI][EÉEeè]";
        
        // Adopte/adopto avec tolérance
        String adopteTolerante = "[AaOo0][DdOo0][OoO0][PpI][TtI][EÉEeèo]";
        
        // Presente/prosente avec tolérance
        String presenteTolerante = "[PpI][RrI][EÉEeèo][SsI][EÉEeèo][NnIl][TtI][EÉEeè]";
        
        // Executee/oxocutoo avec tolérance
        String executeeTolerante = "[EÉEeèo][XxI][EÉEeèo][CcG][UuV][TtI][EÉEeèo][EÉEeèo]";
        
        headerRepubliquePattern = Pattern.compile(
            safePatternOrDefault(patternHeaderRepublique, "(?i)REPUBLIQUE")
                .replace("RÉPUBLIQUE", republiqueTolerante).replace("REPUBLIQUE", republiqueTolerante)
                .replace("BÉNIN", beninTolerant).replace("BENIN", beninTolerant).replace(" ", "\\s+"),
            flags
        );
        headerDevisePattern = Pattern.compile(
            safePatternOrDefault(patternHeaderDevise, "(?i)FRATERNITE|FRATERNITÉ").replace(" ", "\\s+"),
            flags
        );
        headerPresidencePattern = Pattern.compile(
            safePatternOrDefault(patternHeaderPresidence, "(?i)PRÉSIDENCE|PRESIDENCE")
                .replace("PRÉSIDENCE", presidenceTolerante).replace("PRESIDENCE", presidenceTolerante)
                .replace("RÉPUBLIQUE", republiqueTolerante).replace("REPUBLIQUE", republiqueTolerante).replace(" ", "\\s+"),
            flags
        );
        // Titre tolérant: N suivi de n'importe quel symbole (°, ", o, O, 0, º)
        titleLoiPattern = Pattern.compile(
            safePatternOrDefault(patternTitleLoi, "(?i)(LOI|DÉCRET|DECRET)\\s*N[°ºo\"O0]")
                .replace(" ", "\\s+"),  // Tolérer espaces multiples
            flags
        );
        // Visa Assemblée avec tolérance maximale (accepte Assembloo, Assemblee, dolibore, adopto, etc.)
        visaAssembleePattern = Pattern.compile(
            safePatternOrDefault(patternVisaAssemblee, "(?i)ASSEMBLÉE NATIONALE|ASSEMBLEE NATIONALE")
                .replace("ASSEMBLÉE", assembleeTolerante).replace("ASSEMBLEE", assembleeTolerante)
                .replace("Vu", "[VvUu]+")
                .replace("délibéré", delibereTolerante).replace("delibere", delibereTolerante)
                .replace("adopté", adopteTolerante).replace("adopte", adopteTolerante)
                .replace(" ", "\\s+"),  // Tolérer OCR et espaces multiples
            flags
        );
        visaPromulgationPattern = Pattern.compile(
            safePatternOrDefault(patternVisaPromulgation, "(?i)Promulgue|Promulgation").replace(" ", "\\s+"),
            flags
        );
        visaArticle1Pattern = Pattern.compile(safePatternOrDefault(patternVisaArticle1, "(?i)Article\\s+(?:1er|premier|\\d+)"), flags);
        // Corps fin avec tolérance maximale (présente/prosente, sera, exécutée/oxocutoo)
        corpsFinPattern = Pattern.compile(
            safePatternOrDefault(patternCorpsFin, "(?i)sera exécutée comme loi|abroge|La présente loi")
                .replace("présente", presenteTolerante).replace("presente", presenteTolerante)
                .replace("sera", "[sS][eéèEo][rRIl][aàoO]")
                .replace("exécutée", executeeTolerante).replace("executee", executeeTolerante)
                .replace(" ", "\\s+"),
            flags
        );
        articlePattern = Pattern.compile(safePatternOrDefault(patternArticle, "(?i)^(?:Article\\s+(?:1er|premier|\\d+))"), flags);
        // Pied avec tolérance (Fait o Cotonou accepté - o au lieu de à)
        piedDebutPattern = Pattern.compile(
            safePatternOrDefault(patternPiedDebut, "(?i)Fait à")
                .replace("Fait", "[FfEe][aàoO0][IilL1][tT]")
                .replace(" à ", "\\s+[aàoO0]\\s+").replace(" le ", "\\s+[lI1][eéèE]\\s+"),
            flags
        );
        piedFinPattern = Pattern.compile(safePatternOrDefault(patternPiedFin, "(?i)AMPLIATIONS"), flags);
        
        log.info("✅ Compiled {} OCR validation patterns", 11);
        
        // Chargement termes juridiques
        legalTerms = Arrays.stream(legalTermsConfig.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
        log.info("✅ Loaded {} legal terms", legalTerms.size());
        
        // Chargement dictionnaire
        loadFrenchDictionary();

        initialized = true;
    }

    private void ensureInitialized() {
        if (!initialized) {
            try {
                initialize();
            } catch (Exception e) {
                // Sécuriser des patterns non nuls au minimum
                int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                if (headerRepubliquePattern == null) headerRepubliquePattern = Pattern.compile("(?i)REPUBLIQUE", flags);
                if (headerDevisePattern == null) headerDevisePattern = Pattern.compile("(?i)FRATERNITE|FRATERNITÉ", flags);
                if (headerPresidencePattern == null) headerPresidencePattern = Pattern.compile("(?i)PRÉSIDENCE|PRESIDENCE", flags);
                if (titleLoiPattern == null) titleLoiPattern = Pattern.compile("(?i)LOI|DÉCRET|DECRET", flags);
                if (visaAssembleePattern == null) visaAssembleePattern = Pattern.compile("(?i)ASSEMBLÉE NATIONALE|ASSEMBLEE NATIONALE", flags);
                if (visaPromulgationPattern == null) visaPromulgationPattern = Pattern.compile("(?i)Promulgue|Promulgation", flags);
                if (visaArticle1Pattern == null) visaArticle1Pattern = Pattern.compile("(?i)Article\\s+(?:1er|premier|\\d+)", flags);
                if (corpsFinPattern == null) corpsFinPattern = Pattern.compile("(?i)sera exécutée comme loi|abroge|La présente loi", flags);
                if (articlePattern == null) articlePattern = Pattern.compile("(?i)^(?:Article\\s+(?:1er|premier|\\d+))", flags);
                if (piedDebutPattern == null) piedDebutPattern = Pattern.compile("(?i)Fait à", flags);
                if (piedFinPattern == null) piedFinPattern = Pattern.compile("(?i)AMPLIATIONS", flags);
                initialized = true;
            }
        }
    }

    private String safePattern(String value) {
        if (value == null) return "a^"; // no match
        String v = value.trim();
        if (v.isEmpty()) return "a^";
        if (v.startsWith("${") && v.endsWith("}")) return "a^";
        return v;
    }

    private String safePatternOrDefault(String value, String defaultRegex) {
        String cleaned = safePattern(value);
        if ("a^".equals(cleaned)) {
            return defaultRegex;
        }
        return cleaned;
    }
    
    /**
     * Charge le dictionnaire français depuis les ressources
     */
    private void loadFrenchDictionary() {
        frenchDictionary = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    getClass().getResourceAsStream("/liste.de.mots.francais.frgut.txt"),
                    StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim().toLowerCase();
                if (!word.isEmpty()) {
                    frenchDictionary.add(word);
                }
            }
            log.info("✅ Loaded French dictionary: {} words", frenchDictionary.size());
        } catch (IOException e) {
            log.error("❌ Failed to load French dictionary: {}", e.getMessage());
            frenchDictionary = Collections.emptySet();
        }
    }

    @Override
        public int detectUnrecognizedWords(String text, String documentId) {
        if (text == null || text.isBlank()) {
                return 0;
        }
        Set<String> words = getUnrecognizedWords(text);
        if (documentId != null && !words.isEmpty()) {
            unrecognizedWordsService.recordUnrecognizedWords(words, documentId);
            log.info("INFO  [{}] Recorded {} new unrecognized words", documentId, words.size());
        }
            return words.size();
    }
    
    @Override
    public double calculateConfidence(String text, List<Article> articles) {
        return calculateConfidence(text, articles, null);
    }
    
    @Override
    public double calculateConfidence(String text, List<Article> articles, String documentId) {
        if (text == null || text.isEmpty() || articles.isEmpty()) {
            return 0.0;
        }
        
        // 1. Score basé sur le nombre d'articles (20%)
        double articleScore = Math.min(articles.size() / 10.0, 1.0);
        
        // 2. Score basé sur la séquentialité des index (20%)
        double sequenceScore = validateSequence(articles);
        
        // 3. Score basé sur la longueur du texte (15%)
        double textLengthScore = Math.min(text.length() / 5000.0, 1.0);
        
        // 4. Score dictionnaire (25%)
        Set<String> unrecognizedWords = getUnrecognizedWords(text);
        double unrecRate = calculateUnrecognizedRate(text);
        
        // Enregistrer les mots non reconnus si documentId fourni
        if (documentId != null && !unrecognizedWords.isEmpty()) {
            unrecognizedWordsService.recordUnrecognizedWords(unrecognizedWords, documentId);
        }
        
        double unrecPenalty = unrecognizedWordsService.calculateUnrecognizedPenalty(unrecRate, unrecognizedWords.size());
        double dictScore = 1.0 - unrecPenalty;
        
        // 5. Score termes juridiques (20%)
        int legalTermsCount = countLegalTerms(text);
        double legalScore = Math.min(legalTermsCount / 8.0, 1.0);
        
        // Pondération finale
        double confidence = (articleScore * 0.20) + (sequenceScore * 0.20) + 
                          (textLengthScore * 0.15) + (dictScore * 0.25) + (legalScore * 0.20);
        
        if (log.isDebugEnabled()) {
            log.debug("Confidence calculation: articles={}, sequence={}, text={}, dict={} (unrecRate={:.2f}%, penalty={:.2f}%), legal={} → total={}",
                    articleScore, sequenceScore, textLengthScore, dictScore, 
                    unrecRate * 100, unrecPenalty * 100, legalScore, confidence);
        }
        
        return confidence;
    }
    
    @Override
    public double validateSequence(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return 0.0;
        }
        
        if (articles.size() == 1) {
            return articles.get(0).getIndex() == 1 ? 1.0 : 0.8;
        }
        
        int gaps = 0;
        int duplicates = 0;
        int outOfOrder = 0;

        List<Integer> indexes = articles.stream()
            .map(Article::getIndex)
            .filter(Objects::nonNull)
            .toList();

        // Duplicates
        Set<Integer> seen = new HashSet<>();
        for (Integer idx : indexes) {
            if (!seen.add(idx)) {
                duplicates++;
            }
        }

        // Gaps across min..max
        int min = Collections.min(indexes);
        int max = Collections.max(indexes);
        Set<Integer> set = new HashSet<>(indexes);
        for (int i = min; i <= max; i++) {
            if (!set.contains(i)) {
                gaps++;
            }
        }

        // Out-of-order
        for (int i = 1; i < indexes.size(); i++) {
            if (indexes.get(i) < indexes.get(i - 1)) {
                outOfOrder++;
            }
        }

        double score = 1.0 - (gaps * 0.15) - (duplicates * 0.25) - (outOfOrder * 0.30);
        score = Math.max(0.0, score);

        if (log.isDebugEnabled()) {
            log.debug("Sequence quality: {} articles, {} gaps, {} duplicates, {} out-of-order → score={}",
                indexes.size(), gaps, duplicates, outOfOrder, score);
        }

        return score;
    }
     /**
      * Extrait les mots non reconnus du texte
      */
    private Set<String> getUnrecognizedWords(String text) {
        if (frenchDictionary.isEmpty()) {
            return Collections.emptySet();
        }
        
            return Pattern.compile("\\p{L}+").matcher(text.toLowerCase())
            .results()
            .map(m -> m.group())
            .filter(w -> w.length() >= 3) // Minimum 3 caractères
            .filter(w -> !frenchDictionary.contains(w))
            .collect(Collectors.toSet());
    }
    
    /**
     * Calcule le taux de mots non reconnus
     */
    private double calculateUnrecognizedRate(String text) {
        if (frenchDictionary.isEmpty()) {
            return 0.0;
        }
        
            List<String> allWords = Pattern.compile("\\p{L}+").matcher(text.toLowerCase())
            .results()
            .map(m -> m.group())
            .filter(w -> w.length() >= 3)
            .toList();
        
        if (allWords.isEmpty()) {
            return 0.0;
        }
        
        long unrecognizedCount = allWords.stream()
            .filter(w -> !frenchDictionary.contains(w))
            .count();
        
        return (double) unrecognizedCount / allWords.size();
    }

    @Override
    public double validateDictionary(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        // Score basé sur le taux de mots reconnus
        double unrecRate = calculateUnrecognizedRate(text);
        return Math.max(0.0, 1.0 - unrecRate);
    }
    
    /**
     * Valide la structure complète d'un document de loi OCR.
     * Vérifie les 5 parties obligatoires : entête, titre, visa, corps, pied.
     */
    @Override
    public double validateDocumentStructure(String text) {
        ensureInitialized();
        if (text == null) {
            log.debug("❌ Null OCR content provided");
            return 0.0;
        }
        if (text.isBlank()) {
            return 0.0;
        }
        
        int sectionsFound = 0;
        int totalSections = 5;
        
        // 1. Vérifier Entête (RÉPUBLIQUE + devise + PRÉSIDENCE)
        boolean hasRepublique = headerRepubliquePattern.matcher(text).find();
        boolean hasDevise = headerDevisePattern.matcher(text).find();
        boolean hasPresidence = headerPresidencePattern.matcher(text).find();
        
        if (hasRepublique && hasDevise && hasPresidence) {
            sectionsFound++;
            log.debug("✅ Entête complet détecté (RÉPUBLIQUE + devise + PRÉSIDENCE)");
        } else {
            log.debug("❌ Entête incomplet : RÉPUBLIQUE={}, devise={}, PRÉSIDENCE={}", 
                     hasRepublique, hasDevise, hasPresidence);
        }
        
        // 2. Vérifier Titre (LOI N°...)
        if (titleLoiPattern.matcher(text).find()) {
            sectionsFound++;
            log.debug("✅ Titre détecté (LOI N°...)");
        } else {
            log.debug("❌ Titre non détecté (LOI N°...)");
        }
        
        // 3. Vérifier Visa (L'assemblée nationale a délibéré...)
        if (visaAssembleePattern.matcher(text).find()) {
            sectionsFound++;
            log.debug("✅ Visa détecté (L'assemblée nationale a délibéré...)");
        } else {
            log.debug("❌ Visa non détecté (L'assemblée nationale a délibéré...)");
        }
        
        // 4. Vérifier Corps (fin avec formule standard)
        if (corpsFinPattern.matcher(text).find()) {
            sectionsFound++;
            log.debug("✅ Fin du corps détectée (sera exécutée comme loi / abroge...)");
        } else {
            log.debug("❌ Fin du corps non détectée");
        }
        
        // 5. Vérifier Pied (Fait à ... AMPLIATIONS)
        boolean hasFait = piedDebutPattern.matcher(text).find();
        boolean hasAmpliations = piedFinPattern.matcher(text).find();
        
        if (hasFait && hasAmpliations) {
            sectionsFound++;
            log.debug("✅ Pied complet détecté (Fait à... + AMPLIATIONS)");
        } else {
            log.debug("❌ Pied incomplet : Fait={}, AMPLIATIONS={}", hasFait, hasAmpliations);
        }
        
        double score = (double) sectionsFound / totalSections;
        log.debug("📋 Structure document : {}/{} sections présentes → score={}", 
                 sectionsFound, totalSections, score);
        
        return score;
    }
    
    /**
     * Compte les termes juridiques présents dans le texte
     */
    private int countLegalTerms(String text) {
        String lowerText = text.toLowerCase();
        return (int) legalTerms.stream()
            .filter(lowerText::contains)
            .count();
    }
}
