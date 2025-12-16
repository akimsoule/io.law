package bj.gouv.sgg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Modèle document simplifié sans annotations JPA.
 * Utilisé pour stockage JSON.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRecord {
    
    private String documentId; // Format: type-year-number (ex: loi-2024-15)
    private String type; // loi ou decret
    private int year;
    private int number;
    private ProcessingStatus status;
    private String url; // URL sur sgg.gouv.bj
    private String title; // Titre du document
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String pdfPath;
    private String ocrPath;
    private String jsonPath;
    private String errorMessage;
    
    public String getDocumentId() {
        if (documentId == null && type != null && !type.isEmpty()) {
            documentId = String.format("%s-%d-%d", type, year, number);
        }
        return documentId;
    }
    
    public static DocumentRecord create(String type, int year, int number) {
        return DocumentRecord.builder()
            .type(type)
            .year(year)
            .number(number)
            .status(ProcessingStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }
}
