package bj.gouv.sgg.service;

/**
 * Interface pour la correction de texte OCR
 */
public interface CorrectOcrText {
    
    /**
     * Applique les corrections OCR au texte brut
     * 
     * @param text Texte OCR brut
     * @return Texte corrigé
     */
    String applyCorrections(String text);
}
