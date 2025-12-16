package bj.gouv.sgg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO représentant le résultat d'extraction OCR → JSON.
 * 
 * <p>Format JSON sauvegardé :
 * <pre>
 * {
 *   "articles": [...],
 *   "metadata": {...},
 *   "confidence": 0.85,
 *   "method": "OCR",
 *   "timestamp": "2025-12-15T16:40:00"
 * }
 * </pre>
 * 
 * @author io.law
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrExtractionResult {
    
    /**
     * Articles extraits du document
     */
    private List<Article> articles;
    
    /**
     * Métadonnées du document (titre, date, signataires)
     */
    private DocumentMetadata metadata;
    
    /**
     * Score de confiance de l'extraction (0.0 - 1.0)
     */
    private double confidence;
    
    /**
     * Méthode d'extraction utilisée (ex: "OCR", "IA-Ollama", "IA-Groq")
     */
    private String method;
    
    /**
     * Timestamp de l'extraction (ISO 8601 format)
     */
    private String timestamp;
}
