package bj.gouv.sgg.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Résultat d'une transformation PDF → JSON.
 * 
 * <p>Contient :
 * <ul>
 *   <li>Le JSON structuré généré</li>
 *   <li>La confiance de l'extraction (0.0 à 1.0)</li>
 *   <li>La source de transformation (OCR, AI:OLLAMA, etc.)</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JsonResult {
    
    /**
     * JSON structuré généré (format String).
     */
    private String json;
    
    /**
     * Confiance de l'extraction (0.0 à 1.0).
     * 
     * <p>Exemples :
     * <ul>
     *   <li>OCR de base : 0.5-0.7</li>
     *   <li>OCR + corrections CSV : 0.7-0.8</li>
     *   <li>IA correction OCR : 0.9</li>
     *   <li>IA extraction complète : 0.95</li>
     * </ul>
     */
    private double confidence;
    
    /**
     * Source de la transformation.
     * 
     * <p>Valeurs possibles :
     * <ul>
     *   <li>{@code OCR} : Extraction OCR de base</li>
     *   <li>{@code OCR:CSV} : OCR + corrections CSV</li>
     *   <li>{@code AI:CORRECTED_OCR} : IA correction du texte OCR</li>
     *   <li>{@code AI:OLLAMA} : IA extraction via Ollama</li>
     *   <li>{@code AI:GROQ} : IA extraction via Groq</li>
     *   <li>{@code AI:FULL} : IA extraction complète (PDF direct)</li>
     * </ul>
     */
    private String source;
}
