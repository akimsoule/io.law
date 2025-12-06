package bj.gouv.sgg.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity pour tracker l'état de traitement des PDFs
 */
@Entity
@Table(name = "pdf_processing_status")
public class ProcessingStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String pdfFileName;

    @Column(nullable = false)
    private String pdfPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingState state = ProcessingState.PENDING;

    @Column(nullable = false)
    private Double confidenceScore = 0.0;

    @Column
    private Integer retryCount = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    private String outputJsonPath;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime parsedAt;

    @Column
    private LocalDateTime validatedAt;

    public enum ProcessingState {
        PENDING,        // Pas encore traité
        PARSING,        // En cours de parsing
        PARSED,         // Parsé mais pas encore validé
        VALIDATING,     // En cours de validation QA
        VALIDATED,      // Validé et confiance >= seuil
        FAILED,         // Échec définitif après retries
        SKIPPED         // Ignoré (IA désactivée)
    }

    // Getters et setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPdfFileName() { return pdfFileName; }
    public void setPdfFileName(String pdfFileName) { this.pdfFileName = pdfFileName; }

    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath; }

    public ProcessingState getState() { return state; }
    public void setState(ProcessingState state) { this.state = state; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getOutputJsonPath() { return outputJsonPath; }
    public void setOutputJsonPath(String outputJsonPath) { this.outputJsonPath = outputJsonPath; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getParsedAt() { return parsedAt; }
    public void setParsedAt(LocalDateTime parsedAt) { this.parsedAt = parsedAt; }

    public LocalDateTime getValidatedAt() { return validatedAt; }
    public void setValidatedAt(LocalDateTime validatedAt) { this.validatedAt = validatedAt; }
}
