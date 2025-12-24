package bj.gouv.sgg.service.correction.impl;

import lombok.extern.slf4j.Slf4j;
import org.languagetool.JLanguageTool;
import org.languagetool.language.French;
import org.languagetool.rules.Rule;
import org.languagetool.rules.RuleMatch;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Slf4j
public class LinguisticCorrectionImpl {

    private final JLanguageTool langTool;

    public LinguisticCorrectionImpl() {
        // 1. Initialisation pour le français
        this.langTool = new JLanguageTool(new French());

        // 2. Optimisation : On désactive tout ce qui n'est pas purement orthographique (TYPOS)
        // Cela améliore la vitesse de traitement pour des mots isolés (OCR).
        for (Rule rule : this.langTool.getAllRules()) {
            if (!"TYPOS".equals(rule.getCategory().getId().toString())) {
                this.langTool.disableRule(rule.getId());
            }
        }
        log.info("✅ LanguageTool initialisé avec succès (Catégorie TYPOS uniquement).");
    }

    /**
     * Analyse un mot inconnu et propose une correction si le score de confiance est élevé.
     *
     * @param word Le mot issu de l'OCR
     * @return La suggestion corrigée ou null si aucune correction sûre n'est trouvée.
     */
    public String getSafeSuggestion(String word) {
        if (word == null || word.isBlank() || word.length() <= 3) {
            return null;
        }

        // Si le mot est un acronyme probable (tout en majuscules), on ne le touche pas
        if (isAllUpperCase(word)) {
            return null;
        }

        try {
            List<RuleMatch> matches = langTool.check(word);

            // Critères de sécurité pour une correction "sûre" :
            // - Une seule zone d'erreur détectée
            // - Maximum 2 suggestions de remplacement proposées
            if (matches.size() == 1) {
                List<String> suggestions = matches.get(0).getSuggestedReplacements();
                if (!suggestions.isEmpty() && suggestions.size() <= 2) {
                    return matchCase(word, suggestions.get(0));
                }
            }
        } catch (IOException e) {
            log.error("❌ Erreur lors de l'appel à LanguageTool pour le mot '{}' : {}", word, e.getMessage());
        }
        return null;
    }

    /**
     * Applique la casse du mot original sur la suggestion proposée.
     */
    public String matchCase(String original, String replacement) {
        if (original == null || original.isEmpty() || replacement == null) {
            return replacement;
        }

        // Cas : "ETAT" -> "ÉTAT"
        if (isAllUpperCase(original) && original.length() > 1) {
            return replacement.toUpperCase(Locale.ROOT);
        }

        // Cas : "Ministàre" -> "Ministère"
        if (Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(replacement.charAt(0)) +
                    (replacement.length() > 1 ? replacement.substring(1).toLowerCase(Locale.ROOT) : "");
        }

        // Cas par défaut : "economie" -> "économie"
        return replacement.toLowerCase(Locale.ROOT);
    }

    private boolean isAllUpperCase(String s) {
        return s.equals(s.toUpperCase(Locale.ROOT)) && !s.equals(s.toLowerCase(Locale.ROOT));
    }

}
