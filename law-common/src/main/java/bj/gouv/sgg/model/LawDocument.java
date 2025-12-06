package bj.gouv.sgg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawDocument {
    
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
    
    public String getDocumentId() {
        return String.format("%s-%d-%d", type, year, number);
    }
    
    public String getPdfFilename() {
        return String.format("%s-%d-%d.pdf", type, year, number);
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
