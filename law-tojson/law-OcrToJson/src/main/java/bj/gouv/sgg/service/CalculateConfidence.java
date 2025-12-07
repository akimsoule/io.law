package bj.gouv.sgg.service;

import bj.gouv.sgg.model.Article;

import java.util.List;

/**
 * Interface pour le calcul de confiance d'extraction OCR
 */
public interface CalculateConfidence {
    
    /**
     * Calcule la confiance de l'extraction basée sur le texte et les articles extraits
     * 
     * @param text Texte OCR brut
     * @param articles Articles extraits
     * @return Score de confiance entre 0.0 et 1.0
     */
    double calculateConfidence(String text, List<Article> articles);
}
