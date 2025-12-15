package bj.gouv.sgg.ai.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * Réponse reçue d'un provider IA.
 */
@Value
@Builder
public class AIResponse {
    
    /**
     * Texte généré par l'IA.
     */
    String generatedText;
    
    /**
     * Modèle utilisé.
     */
    String model;
    
    /**
     * Provider source.
     */
    String provider;
    
    /**
     * Nombre de tokens consommés (prompt + completion).
     */
    int tokensUsed;
    
    /**
     * Durée de traitement côté provider (ms).
     */
    long processingTimeMs;
    
    /**
     * Timestamp de la réponse.
     */
    @Builder.Default
    Instant timestamp = Instant.now();
    
    /**
     * Métadonnées additionnelles du provider.
     */
    @Builder.Default
    Map<String, Object> metadata = Map.of();
    
    /**
     * Indique si la réponse a été tronquée (limite tokens atteinte).
     */
    @Builder.Default
    boolean truncated = false;
    
    // ===== Méthodes de compatibilité pour les tests =====
    
    /**
     * Alias pour generatedText (compatibilité tests).
     */
    public String getText() {
        return generatedText;
    }
    
    /**
     * Retourne le nombre de tokens du prompt (non disponible dans Ollama).
     * Pour Ollama, on retourne 0 car seul eval_count est disponible.
     */
    public int getPromptTokens() {
        return 0; // Ollama ne fournit pas cette métrique séparément
    }
    
    /**
     * Retourne le nombre de tokens de complétion.
     * Pour Ollama, c'est équivalent à tokensUsed (eval_count).
     */
    public int getCompletionTokens() {
        return tokensUsed;
    }
    
    /**
     * Retourne le nombre total de tokens.
     */
    public int getTotalTokens() {
        return tokensUsed;
    }
}
