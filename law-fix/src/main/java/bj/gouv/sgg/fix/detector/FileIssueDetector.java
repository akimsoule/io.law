package bj.gouv.sgg.fix.detector;

import bj.gouv.sgg.fix.model.Issue;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Détecteur de problèmes de fichiers manquants ou corrompus.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileIssueDetector {
    
    private final FileStorageService fileStorageService;
    
    /**
     * Détecte les problèmes de fichiers pour un document.
     */
    public List<Issue> detect(LawDocument document) {
        List<Issue> issues = new ArrayList<>();
        String docId = document.getDocumentId();
        
        // Vérifier PDF manquant
        if ((document.getStatus() == LawDocument.ProcessingStatus.DOWNLOADED ||
            document.getStatus() == LawDocument.ProcessingStatus.EXTRACTED ||
            document.getStatus() == LawDocument.ProcessingStatus.CONSOLIDATED) &&
            !fileStorageService.pdfExists(document.getType(), docId)) {
            {
                issues.add(Issue.builder()
                    .documentId(docId)
                    .type(Issue.IssueType.MISSING_PDF)
                    .severity(Issue.IssueSeverity.CRITICAL)
                    .description("PDF manquant alors que status=" + document.getStatus())
                    .currentStatus(document.getStatus().name())
                    .suggestedAction("Re-télécharger PDF depuis SGG")
                    .detectedAt(LocalDateTime.now())
                    .autoFixable(true)
                    .build());
                
                log.warn("🔴 [{}] PDF manquant (status={})", docId, document.getStatus());
            }
        
        // Vérifier OCR manquant
        if ((document.getStatus() == LawDocument.ProcessingStatus.EXTRACTED ||
            document.getStatus() == LawDocument.ProcessingStatus.CONSOLIDATED) &&
            !fileStorageService.ocrExists(document.getType(), docId)) {
            {
                issues.add(Issue.builder()
                    .documentId(docId)
                    .type(Issue.IssueType.MISSING_OCR)
                    .severity(Issue.IssueSeverity.HIGH)
                    .description("OCR manquant alors que status=" + document.getStatus())
                    .currentStatus(document.getStatus().name())
                    .suggestedAction("Re-exécuter extraction OCR")
                    .detectedAt(LocalDateTime.now())
                    .autoFixable(true)
                    .build());
                
                log.warn("⚠️  [{}] OCR manquant (status={})", docId, document.getStatus());
            }
        
        // Vérifier JSON manquant
        if ((document.getStatus() == LawDocument.ProcessingStatus.EXTRACTED ||
            document.getStatus() == LawDocument.ProcessingStatus.CONSOLIDATED) &&
            !fileStorageService.jsonExists(document.getType(), docId)) {
            {
                issues.add(Issue.builder()
                    .documentId(docId)
                    .type(Issue.IssueType.MISSING_JSON)
                    .severity(Issue.IssueSeverity.CRITICAL)
                    .description("JSON manquant alors que status=" + document.getStatus())
                    .currentStatus(document.getStatus().name())
                    .suggestedAction("Re-parser OCR → JSON")
                    .detectedAt(LocalDateTime.now())
                    .autoFixable(true)
                    .build());
                
                log.warn("🔴 [{}] JSON manquant (status={})", docId, document.getStatus());
            }
        
        return issues;
    }
}
