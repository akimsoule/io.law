package bj.gouv.sgg.service;

import bj.gouv.sgg.exception.IAException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface pour les services de correction OCR via IA.
 * 
 * <p><b>Focus</b> : Correction de texte OCR brut, PAS extraction JSON directe.
 * 
 * <p><b>Workflow</b> :
 * <pre>
 * Texte OCR brut → IA correction → Texte corrigé
 * </pre>
 * 
 * <p><b>Implémentations</b> :
 * <ul>
 *   <li><b>OllamaClient</b> : Correction via Ollama (local)</li>
 *   <li><b>GroqClient</b> : Correction via Groq (API cloud)</li>
 *   <li><b>NoClient</b> : Pas de correction IA disponible</li>
 * </ul>
 * 
 * @see AiOcrCorrectionService
 */
public interface IAService {

    Logger LOGGER = LoggerFactory.getLogger(IAService.class);

    /**
     * Corrige un texte OCR brut via IA.
     * 
     * <p><b>Objectif</b> : Corriger les erreurs OCR courantes :
     * <ul>
     *   <li>"Articlc" → "Article"</li>
     *   <li>"1e" → "1er"</li>
     *   <li>"A rticle " → "Article"</li>
     *   <li>Espaces incorrects, majuscules erronées</li>
     * </ul>
     * 
     * <p><b>Anti-hallucination</b> : L'IA NE DOIT PAS inventer de contenu,
     * seulement corriger les erreurs évidentes de reconnaissance OCR.
     * 
     * @param rawOcrText Texte OCR brut non corrigé
     * @param prompt Instructions de correction pour l'IA
     * @return Texte OCR corrigé
     * @throws IAException Si la correction échoue
     */
    String correctOcrText(String rawOcrText, String prompt) throws IAException;

    /**
     * Extrait un JSON structuré depuis un texte OCR.
     * 
     * <p><b>Objectif</b> : Analyser le texte OCR et extraire les métadonnées,
     * articles, signataires au format JSON structuré.
     * 
     * @param ocrText Texte OCR (corrigé ou brut)
     * @return JSON structuré contenant les métadonnées et articles
     * @throws IAException Si l'extraction échoue
     */
    com.google.gson.JsonObject extractJsonFromOcr(String ocrText) throws IAException;

    /**
     * Retourne le nom de la source IA (ex: "OLLAMA:gemma3n", "GROQ:llama3")
     */
    String getSourceName();
    
    /**
     * Vérifie si le service IA est disponible et opérationnel.
     * 
     * <p><b>Implémentations</b> :
     * <ul>
     *   <li><b>OllamaClient</b> : Ping Ollama + vérifier modèle disponible</li>
     *   <li><b>GroqClient</b> : Vérifier API key configurée + serveur accessible</li>
     *   <li><b>NoClient</b> : Toujours false (pas d'IA)</li>
     * </ul>
     * 
     * @return true si le service est disponible, false sinon
     */
    boolean isAvailable();

}
