package bj.gouv.sgg.entity;

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
     * Document introuvable sur le serveur (HTTP 404)
     */
    NOT_FOUND,
    
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
    OCRED_V2,
    
    /**
     * Articles extraits depuis OCR (fichier .json créé avec méthode OCR)
     */
    EXTRACTED,
    
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
     * Échec lors du téléchargement du PDF
     */
    FAILED_DOWNLOAD,
    
    /**
     * Échec lors de l'extraction OCR du PDF
     */
    FAILED_OCR,
    
    /**
     * Échec lors de l'extraction des articles depuis OCR
     */
    FAILED_EXTRACTION,
    
    /**
     * Échec lors de la validation des articles
     */
    FAILED_VALIDATION,
    
    /**
     * Échec lors de l'amélioration IA
     */
    FAILED_AI,
    
    /**
     * Échec lors de la consolidation finale
     */
    FAILED_CONSOLIDATION,
    
    /**
     * Fichier PDF corrompu (PNG déguisé, tronqué, etc.)
     */
    FAILED_CORRUPTED;

}
