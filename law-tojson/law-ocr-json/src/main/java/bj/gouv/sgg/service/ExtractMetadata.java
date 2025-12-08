package bj.gouv.sgg.service;


import bj.gouv.sgg.model.DocumentMetadata;

/**
 * Interface pour l'extraction de métadonnées depuis du texte OCR
 */
public interface ExtractMetadata {
    
    /**
     * Extrait les métadonnées depuis le texte OCR
     * 
     * @param text Texte OCR brut
     * @return Métadonnées extraites
     */
    DocumentMetadata extractMetadata(String text);
}
