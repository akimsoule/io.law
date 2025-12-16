package bj.gouv.sgg.ai.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * Résultat d'une transformation IA.
 * 
 * @param <O> Type de sortie produite
 */
@Value
@Builder
public class TransformationResult<O> {
    
    /**
     * Sortie produite par la transformation.
     */
    O output;
    
    /**
     * Confiance dans le résultat (0.0 à 1.0).
     */
    double confidence;
    
    /**
     * Méthode utilisée (ex: "IA:OLLAMA:gemma3n", "OCR", "HYBRID").
     */
    String method;
    
    /**
     * Timestamp de la transformation.
     */
    @Builder.Default
    Instant timestamp = Instant.now();
    
    /**
     * Durée de traitement en millisecondes.
     */
    long durationMs;
    
    /**
     * Nombre de chunks traités.
     */
    @Builder.Default
    int chunksProcessed = 1;
    
    /**
     * Nombre de tokens consommés (estimation).
     */
    int tokensUsed;
    
    /**
     * Warnings éventuels (non bloquants).
     */
    @Builder.Default
    java.util.List<String> warnings = java.util.List.of();
    
    /**
     * Métadonnées additionnelles pour debug/audit.
     */
    @Builder.Default
    Map<String, Object> metadata = Map.of();
    
    /**
     * Succès de la transformation.
     */
    @Builder.Default
    boolean success = true;
    
    /**
     * Message d'erreur si échec.
     */
    String errorMessage;
}
