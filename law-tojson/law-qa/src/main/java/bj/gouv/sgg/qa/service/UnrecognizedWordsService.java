package bj.gouv.sgg.qa.service;

import java.util.Set;

/**
 * Service de gestion des mots non reconnus.
 * 
 * <p>Persiste et analyse les mots OCR non reconnus pour :
 * <ul>
 *   <li>Identifier les erreurs OCR récurrentes</li>
 *   <li>Enrichir le fichier de corrections</li>
 *   <li>Calculer des pénalités de confiance</li>
 * </ul>
 */
public interface UnrecognizedWordsService {
    
    /**
     * Enregistre les mots non reconnus pour un document.
     * 
     * @param words Ensemble de mots non reconnus
     * @param documentId ID du document source
     */
    void recordUnrecognizedWords(Set<String> words, String documentId);
    
    /**
     * Calcule la pénalité basée sur le taux de mots non reconnus.
     * 
     * @param unrecognizedRate Taux de mots non reconnus (0.0-1.0)
     * @param totalUnrecognized Nombre total de mots non reconnus
     * @return Pénalité entre 0.0 (aucune) et 1.0 (maximale)
     */
    double calculateUnrecognizedPenalty(double unrecognizedRate, int totalUnrecognized);
    
    /**
     * Récupère le nombre total de mots non reconnus enregistrés.
     * 
     * @return Nombre de mots uniques non reconnus
     */
    int getTotalUnrecognizedWordsCount();
    
    /**
     * Charge les mots non reconnus existants.
     * 
     * @return Ensemble des mots déjà enregistrés
     */
    Set<String> loadExistingWords();
}
