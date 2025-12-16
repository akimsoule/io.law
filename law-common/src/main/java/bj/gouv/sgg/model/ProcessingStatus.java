package bj.gouv.sgg.model;

/**
 * Statuts de traitement des documents de loi/décret.
 * Représente les étapes du pipeline: fetch → download → extract → consolidate
 */
public enum ProcessingStatus {
    /**
     * Document créé, pas encore traité
     */
    PENDING,
    
    /**
     * Métadonnées récupérées (HTTP HEAD 200 OK)
     */
    FETCHED,
    
    /**
     * PDF téléchargé avec succès
     */
    DOWNLOADED,
    
    /**
     * Extraction OCR effectuée (fichier .txt créé)
     */
    EXTRACTED,
    
    /**
     * Articles extraits depuis OCR (fichier .json créé avec méthode OCR)
     */
    ARTICLES_EXTRACTED,
    
    /**
     * Articles validés par contrôle qualité
     */
    VALIDATED,
    
    /**
     * Articles améliorés par IA (Ollama/Groq)
     */
    AI_ENHANCED,
    
    /**
     * Données consolidées en base de données ou fichiers finaux
     */
    CONSOLIDATED,
    
    /**
     * Erreur générique lors du traitement
     */
    FAILED,
    
    /**
     * Fichier PDF corrompu (PNG déguisé, tronqué, etc.)
     */
    CORRUPTED;
    
    /**
     * Vérifie si le statut indique un traitement terminé (succès ou échec).
     */
    public boolean isTerminal() {
        return this == CONSOLIDATED || this == FAILED || this == CORRUPTED;
    }
    
    /**
     * Vérifie si le statut indique un succès.
     */
    public boolean isSuccess() {
        return this == FETCHED || this == DOWNLOADED || this == EXTRACTED 
            || this == ARTICLES_EXTRACTED || this == VALIDATED 
            || this == AI_ENHANCED || this == CONSOLIDATED;
    }
    
    /**
     * Vérifie si le statut indique un échec.
     */
    public boolean isFailure() {
        return this == FAILED || this == CORRUPTED;
    }
}
