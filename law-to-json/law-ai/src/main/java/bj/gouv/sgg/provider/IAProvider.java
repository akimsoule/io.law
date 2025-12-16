package bj.gouv.sgg.provider;

import bj.gouv.sgg.ai.model.AIRequest;
import bj.gouv.sgg.ai.model.AIResponse;
import bj.gouv.sgg.exception.IAException;

import java.util.Optional;

/**
 * Interface pour les providers d'IA (Ollama, Groq, etc.).
 * 
 * <p><b>Responsabilité</b> : Abstraire l'accès aux modèles IA, quelle que soit la source.
 * 
 * <p><b>Implémentations</b> :
 * <ul>
 *   <li><b>OllamaProvider</b> : Modèles locaux via Ollama</li>
 *   <li><b>GroqProvider</b> : API cloud Groq</li>
 *   <li><b>NoIAProvider</b> : Fallback sans IA (retourne erreur)</li>
 * </ul>
 * 
 * <p><b>Capacités</b> : Chaque provider déclare ses capacités :
 * <ul>
 *   <li>Vision (images base64)</li>
 *   <li>Texte seulement</li>
 *   <li>Limite de contexte (tokens)</li>
 *   <li>Modèles disponibles</li>
 * </ul>
 */
public interface IAProvider {

    /**
     * Nom du provider pour logging et sélection.
     * 
     * @return Nom unique (ex: "OLLAMA", "GROQ", "NO_IA")
     */
    String getProviderName();
    
    /**
     * Vérifie si le provider est disponible et opérationnel.
     * 
     * <p><b>Vérifications</b> :
     * <ul>
     *   <li>Serveur/API accessible</li>
     *   <li>Authentification valide (si nécessaire)</li>
     *   <li>Au moins un modèle disponible</li>
     * </ul>
     * 
     * @return true si disponible, false sinon
     */
    boolean isAvailable();
    
    /**
     * Envoie une requête au provider IA.
     * 
     * <p><b>Gestion des erreurs</b> :
     * <ul>
     *   <li>Timeout réseau</li>
     *   <li>Quota dépassé</li>
     *   <li>Modèle non disponible</li>
     *   <li>Réponse invalide</li>
     * </ul>
     * 
     * @param request Requête avec prompt, images optionnelles, config
     * @return Réponse avec texte généré et métadonnées
     * @throws IAException Si requête échoue
     */
    AIResponse complete(AIRequest request) throws IAException;
    
    /**
     * Retourne les capacités du provider.
     * 
     * @return Capacités (vision, limites, modèles)
     */
    ProviderCapabilities getCapabilities();
    
    /**
     * Retourne la liste des modèles disponibles.
     * 
     * @return Liste des modèles (nom + capacités)
     */
    java.util.List<ModelInfo> getAvailableModels();
    
    /**
     * Sélectionne le meilleur modèle pour une tâche donnée.
     * 
     * @param requiresVision true si images nécessaires
     * @param estimatedTokens Estimation tokens nécessaires
     * @return Modèle optimal ou vide si aucun compatible
     */
    Optional<ModelInfo> selectBestModel(boolean requiresVision, int estimatedTokens);
    
    /**
     * Informations sur les capacités d'un provider.
     */
    record ProviderCapabilities(
        boolean supportsVision,
        boolean supportsStreaming,
        int maxContextTokens,
        int maxImageSizeMB,
        int maxImagesPerRequest
    ) {}
    
    /**
     * Informations sur un modèle IA.
     */
    record ModelInfo(
        String name,
        boolean supportsVision,
        int contextWindow,
        String description
    ) {}
}
