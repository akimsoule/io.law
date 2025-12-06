package bj.gouv.sgg.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "law_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawDocument {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    public static final String TYPE_LOI = "loi";
    public static final String TYPE_DECRET = "decret";
    
    @Column(nullable = false)
    private String type;        // loi ou decret
    
    @Column(name = "document_year", nullable = false)  // "year" est un mot réservé SQL
    private int year;
    
    @Column(nullable = false)
    private int number;
    
    private String url;
    private String pdfPath;
    private String ocrPath;
    private String sha256;
    
    @Column(name = "document_exists")  // "exists" est un mot réservé SQL
    private boolean exists;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProcessingStatus status;
    
    @Transient  // Ne pas persister en base (trop volumineux)
    private byte[] pdfContent;  // Contenu PDF téléchargé
    
    public String getDocumentId() {
        return String.format("%s-%d-%d", type, year, number);
    }
    
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
    
    public enum ProcessingStatus {
        PENDING,
        FETCHED,
        DOWNLOADED,
        EXTRACTED,
        CONSOLIDATED,
        FAILED
    }
}
