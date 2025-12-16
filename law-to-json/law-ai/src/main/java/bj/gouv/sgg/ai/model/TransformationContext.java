package bj.gouv.sgg.ai.model;

import bj.gouv.sgg.provider.IAProvider;
import bj.gouv.sgg.model.LawDocument;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Contexte de transformation contenant toutes les informations nécessaires.
 * 
 * <p><b>Contenu</b> :
 * <ul>
 *   <li>Document source (métadonnées)</li>
 *   <li>Provider IA sélectionné</li>
 *   <li>Configuration transformation</li>
 *   <li>Paramètres additionnels</li>
 * </ul>
 */
@Value
@Builder
public class TransformationContext {
    
    /**
     * Document en cours de traitement (pour métadonnées).
     */
    LawDocument document;
    
    /**
     * Provider IA à utiliser pour cette transformation.
     */
    IAProvider provider;
    
    /**
     * Configuration de la transformation.
     */
    TransformationConfig config;
    
    /**
     * Paramètres additionnels pour personnalisation.
     */
    @Builder.Default
    Map<String, Object> additionalParams = Map.of();
    
    /**
     * Configuration d'une transformation.
     */
    @Value
    @Builder
    public static class TransformationConfig {
        
        /**
         * Force re-transformation même si résultat existe.
         */
        @Builder.Default
        boolean force = false;
        
        /**
         * Température IA (0.0 = déterministe, 1.0 = créatif).
         */
        @Builder.Default
        double temperature = 0.1;
        
        /**
         * Nombre maximum de tokens à générer.
         */
        @Builder.Default
        int maxTokens = 4000;
        
        /**
         * Taille maximale d'un chunk (caractères pour texte, pages pour PDF).
         */
        @Builder.Default
        int chunkSize = 2000;
        
        /**
         * Chevauchement entre chunks pour continuité.
         */
        @Builder.Default
        int chunkOverlap = 200;
        
        /**
         * Timeout en secondes pour requête IA.
         */
        @Builder.Default
        int timeoutSeconds = 300;
        
        /**
         * Active le mode verbose pour logging détaillé.
         */
        @Builder.Default
        boolean verbose = false;
    }
}
