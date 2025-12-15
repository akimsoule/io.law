package bj.gouv.sgg.qa.service;

import bj.gouv.sgg.model.Article;

import java.util.List;

/**
 * Service de validation de la qualité d'extraction OCR.
 * 
 * <p>Calcule un score de confiance basé sur plusieurs critères :
 * <ul>
 *   <li>Nombre d'articles extraits</li>
 *   <li>Qualité de la séquence (gaps, duplicates, ordre)</li>
 *   <li>Longueur du texte</li>
 *   <li>Taux de mots reconnus (dictionnaire français)</li>
 *   <li>Présence de termes juridiques</li>
 * </ul>
 */
public interface OcrQualityService {
    
    /**
     * Calcule le score de confiance de l'extraction OCR.
     * 
     * @param text Texte OCR brut
     * @param articles Articles extraits
     * @return Score de confiance entre 0.0 et 1.0
     */
    double calculateConfidence(String text, List<Article> articles);
    
    /**
     * Calcule le score de confiance avec tracking des mots non reconnus.
     * 
     * @param text Texte OCR brut
     * @param articles Articles extraits
     * @param documentId ID du document (pour logs)
     * @return Score de confiance entre 0.0 et 1.0
     */
    double calculateConfidence(String text, List<Article> articles, String documentId);
    
    /**
     * Valide la qualité de la séquence d'articles.
     * 
     * @param articles Articles à valider
     * @return Score de séquence entre 0.0 (très mauvais) et 1.0 (parfait)
     */
    double validateSequence(List<Article> articles);
    
    /**
     * Valide le taux de mots reconnus dans le texte.
     * 
     * @param text Texte à analyser
     * @return Score entre 0.0 (aucun mot reconnu) et 1.0 (tous reconnus)
     */
    double validateDictionary(String text);
    
    /**
     * Détecte les mots non reconnus dans le texte.
     * 
     * @param text Texte à analyser
     * @param documentId ID du document (pour enregistrement)
     * @return Nombre de mots non reconnus
     */
    int detectUnrecognizedWords(String text, String documentId);
    
    /**
     * Valide la structure complète d'un document de loi OCR.
     * 
     * <p>Vérifie la présence des 5 parties obligatoires :
     * <ol>
     *   <li><b>Entête</b> : RÉPUBLIQUE DU BENIN + Fraternité-Justice-Travail + PRÉSIDENCE</li>
     *   <li><b>Titre</b> : LOI N° ...</li>
     *   <li><b>Visa</b> : L'assemblée nationale a délibéré...</li>
     *   <li><b>Corps</b> : Articles jusqu'à "sera exécutée comme loi de l'État"</li>
     *   <li><b>Pied</b> : Fait à ... jusqu'à AMPLIATIONS</li>
     * </ol>
     * 
     * @param text Texte OCR complet
     * @return score 0.0 à 1.0 (0.2 par section présente)
     */
    double validateDocumentStructure(String text);
}
