package bj.gouv.sgg.ai.transformation;

import bj.gouv.sgg.ai.model.TransformationContext;
import bj.gouv.sgg.ai.model.TransformationResult;
import bj.gouv.sgg.exception.IAException;

/**
 * Interface générique pour toutes les transformations IA.
 * 
 * <p><b>Principe</b> : Une transformation prend une entrée I et produit une sortie O
 * via IA, avec contexte pour métadonnées et configuration.
 * 
 * <p><b>Types de transformations supportées</b> :
 * <ul>
 *   <li><b>PDF → OCR</b> : Extraction texte depuis PDF via IA vision</li>
 *   <li><b>OCR → OCR corrigé</b> : Correction erreurs OCR</li>
 *   <li><b>OCR → JSON</b> : Parsing structure depuis texte</li>
 *   <li><b>JSON → JSON corrigé</b> : Correction attributs/valeurs</li>
 *   <li><b>PDF → JSON</b> : Extraction directe complète</li>
 * </ul>
 * 
 * <p><b>Gestion du contexte</b> :
 * <ul>
 *   <li>Limites de tokens du modèle</li>
 *   <li>Chunking automatique si nécessaire</li>
 *   <li>Métadonnées document (type, année, numéro)</li>
 *   <li>Configuration provider IA</li>
 * </ul>
 * 
 * @param <I> Type d'entrée (Path PDF, String OCR, JsonObject, etc.)
 * @param <O> Type de sortie (String OCR, JsonObject, etc.)
 * 
 * @see TransformationContext
 * @see TransformationResult
 */
public interface IATransformation<I, O> {

    /**
     * Nom de la transformation pour logging et traçabilité.
     * 
     * @return Nom descriptif (ex: "PDF_TO_OCR", "OCR_CORRECTION", "OCR_TO_JSON")
     */
    String getName();
    
    /**
     * Type d'entrée acceptée par cette transformation.
     * 
     * @return Classe du type d'entrée
     */
    Class<I> getInputType();
    
    /**
     * Type de sortie produite par cette transformation.
     * 
     * @return Classe du type de sortie
     */
    Class<O> getOutputType();
    
    /**
     * Exécute la transformation avec le contexte fourni.
     * 
     * <p><b>Comportement</b> :
     * <ol>
     *   <li>Valider entrée et contexte</li>
     *   <li>Appliquer chunking si nécessaire</li>
     *   <li>Appeler provider IA avec prompt optimisé</li>
     *   <li>Parser et valider sortie</li>
     *   <li>Retourner résultat avec métadonnées</li>
     * </ol>
     * 
     * <p><b>Anti-hallucination</b> : Chaque transformation DOIT inclure
     * des instructions strictes pour éviter l'invention de contenu.
     * 
     * @param input Données d'entrée à transformer
     * @param context Contexte de transformation (document, config, provider)
     * @return Résultat avec sortie et métadonnées (confiance, méthode, durée)
     * @throws IAException Si transformation échoue
     */
    TransformationResult<O> transform(I input, TransformationContext context) throws IAException;
    
    /**
     * Vérifie si cette transformation peut s'exécuter avec le contexte donné.
     * 
     * <p><b>Validations</b> :
     * <ul>
     *   <li>Provider IA disponible</li>
     *   <li>Modèle supporte type transformation (vision, texte)</li>
     *   <li>Taille entrée compatible avec limites modèle</li>
     *   <li>Configuration valide</li>
     * </ul>
     * 
     * @param context Contexte à valider
     * @return true si transformation possible, false sinon
     */
    boolean canTransform(TransformationContext context);
    
    /**
     * Estime le nombre de chunks nécessaires pour traiter l'entrée.
     * 
     * <p>Utilisé pour optimiser le traitement et informer l'utilisateur.
     * 
     * @param input Données d'entrée
     * @param context Contexte avec limites du modèle
     * @return Nombre de chunks estimé (1 si pas de chunking)
     */
    int estimateChunks(I input, TransformationContext context);
}
