package bj.gouv.sgg.ai.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Requête envoyée à un provider IA.
 */
@Value
@Builder
public class AIRequest {
    
    /**
     * Modèle à utiliser (ex: "gemma3n", "llama3-vision").
     */
    String model;
    
    /**
     * Prompt textuel.
     */
    String prompt;
    
    /**
     * Images en base64 (optionnel, pour vision).
     */
    @Builder.Default
    List<String> imagesBase64 = List.of();
    
    /**
     * Température (0.0 = déterministe, 1.0 = créatif).
     */
    @Builder.Default
    double temperature = 0.1;
    
    /**
     * Nombre maximum de tokens à générer.
     */
    @Builder.Default
    int maxTokens = 4000;
    
    /**
     * Active le streaming de réponse.
     */
    @Builder.Default
    boolean stream = false;
    
    /**
     * Options additionnelles spécifiques au provider.
     */
    @Builder.Default
    Map<String, Object> providerOptions = Map.of();
}
