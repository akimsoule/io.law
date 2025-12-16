package bj.gouv.sgg.service;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * Service de validation de la qualité des données JSON extraites.
 * 
 * <p>Valide la cohérence et complétude des données JSON :
 * <ul>
 *   <li>Structure du document</li>
 *   <li>Métadonnées obligatoires</li>
 *   <li>Cohérence des articles</li>
 *   <li>Signataires</li>
 * </ul>
 */
public interface JsonQualityService {
    
    /**
     * Valide la structure complète du JSON.
     * 
     * @param jsonContent Contenu JSON à valider
     * @return true si valide, false sinon
     */
    boolean validateStructure(String jsonContent);
    
    /**
     * Valide les métadonnées du document.
     * 
     * @param metadata Objet JSON _metadata
     * @return Score de complétude entre 0.0 et 1.0
     */
    double validateMetadata(JsonObject metadata);
    
    /**
     * Valide la cohérence des index d'articles.
     * 
     * @param articleIndices Liste des index d'articles
     * @return true si cohérent (séquence sans gap), false sinon
     */
    boolean validateArticleIndices(List<Integer> articleIndices);
    
    /**
     * Calcule un score global de qualité JSON.
     * 
     * @param jsonContent Contenu JSON complet
     * @return Score entre 0.0 (très mauvais) et 1.0 (excellent)
     */
    double calculateJsonQualityScore(String jsonContent);
}
