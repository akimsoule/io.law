package bj.gouv.sgg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Document juridique béninois (Loi ou Décret).
 * 
 * <p><b>POJO simple sans JPA</b> : Utilisé pour transfert données entre modules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawDocument {
    
    private Long id;
    
    public static final String TYPE_LOI = "loi";
    public static final String TYPE_DECRET = "decret";
    
    private String type;        // loi ou decret
    private int year;
    private int number;
    
    private String url;
    private String pdfPath;
    private String ocrPath;
    private String sha256;
    
    private boolean exists;
    private ProcessingStatus status;
    
    private byte[] pdfContent;  // Contenu PDF téléchargé
    private String ocrContent;  // Contenu OCR extrait
    
    /**
     * @return documentId au format "type-year-number" (ex: "loi-2024-15")
     */
    public String getDocumentId() {
        return String.format("%s-%d-%d", type, year, number);
    }
    
    /**
     * @return filename PDF (ex: "loi-2024-15.pdf")
     */
    public String getPdfFilename() {
        return String.format("%s-%d-%d.pdf", type, year, number);
    }
    
    /**
     * Parse un documentId au format "type-year-number" et retourne les composants.
     * @param documentId le documentId à parser (ex: "loi-2024-15")
     * @return un tableau [type, year, number] ou tableau vide si format invalide
     */
    public static String[] parseDocumentId(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return new String[0];
        }
        String[] parts = documentId.split("-");
        if (parts.length != 3) {
            return new String[0];
        }
        return parts; // [type, year, number]
    }
}
