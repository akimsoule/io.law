package bj.gouv.sgg.service.extract;

import bj.gouv.sgg.model.Article;

import java.util.List;

/**
 * Interface pour l'extraction d'articles depuis du texte OCR
 */
public interface ExtractArticles {
    
    /**
     * Extrait les articles depuis le texte OCR
     * 
     * @param text Texte OCR brut
     * @return Liste des articles extraits
     */
    List<Article> extractArticles(String text);
}
