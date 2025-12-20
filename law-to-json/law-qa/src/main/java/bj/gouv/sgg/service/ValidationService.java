package bj.gouv.sgg.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Service de validation qualité des documents extraits.
 * 
 * <p>Valide la cohérence et complétude des documents JSON :
 * <ul>
 *   <li>Structure JSON</li>
 *   <li>Métadonnées</li>
 *   <li>Articles</li>
 *   <li>Qualité OCR</li>
 * </ul>
 * 
 * @author io.law
 * @since 1.0.0
 */
public interface ValidationService {
    
    /**
     * Valide un document JSON avec son fichier OCR optionnel.
     * 
     * @param jsonPath Chemin fichier JSON
     * @param ocrPath Chemin fichier OCR (optionnel)
     * @return Résultat validation
     */
    ValidationResult validateDocument(Path jsonPath, Path ocrPath);
    
    /**
     * DTO résultat de validation.
     */
    interface ValidationResult {
        String getDocumentId();
        boolean isValid();
        double getConfidence();
        List<String> getErrors();
    }
}
