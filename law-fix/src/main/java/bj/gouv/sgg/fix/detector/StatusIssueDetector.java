package bj.gouv.sgg.fix.detector;

import bj.gouv.sgg.fix.model.Issue;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.DownloadResultRepository;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Détecteur de documents bloqués dans un statut.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatusIssueDetector {
    
    private final DownloadResultRepository downloadResultRepository;
    private final FileStorageService fileStorageService;
    
    /**
     * Détecte les documents bloqués dans un statut.
     * Un document est considéré bloqué s'il est dans un statut non-final
     * depuis trop longtemps (critère à affiner selon besoin).
     */
    public List<Issue> detect(LawDocument document) {
        List<Issue> issues = new ArrayList<>();
        String docId = document.getDocumentId();
        
        // Documents en PENDING (ne devraient pas rester longtemps)
        if (document.getStatus() == LawDocument.ProcessingStatus.PENDING) {
            issues.add(Issue.builder()
                .documentId(docId)
                .type(Issue.IssueType.STUCK_IN_PENDING)
                .severity(Issue.IssueSeverity.MEDIUM)
                .description("Document en PENDING - devrait passer à FETCHED")
                .currentStatus(document.getStatus().name())
                .suggestedAction("Exécuter fetchCurrentJob pour ce document")
                .detectedAt(LocalDateTime.now())
                .autoFixable(true)
                .build());
            
            log.info("📋 [{}] Bloqué en PENDING", docId);
        }
        
        // Documents en FETCHED (doivent être téléchargés)
        if (document.getStatus() == LawDocument.ProcessingStatus.FETCHED) {
            // Vérifier incohérence : FETCHED mais déjà dans download_results + PDF présent
            boolean inDownloadResults = downloadResultRepository.existsByDocumentId(docId);
            boolean pdfExists = fileStorageService.pdfExists(document.getType(), docId);
            
            if (inDownloadResults && pdfExists) {
                issues.add(Issue.builder()
                    .documentId(docId)
                    .type(Issue.IssueType.STATUS_INCONSISTENT)
                    .severity(Issue.IssueSeverity.MEDIUM)
                    .description("Document FETCHED mais PDF déjà téléchargé et en download_results")
                    .currentStatus(document.getStatus().name())
                    .suggestedAction("Mettre à jour statut vers DOWNLOADED")
                    .detectedAt(LocalDateTime.now())
                    .autoFixable(true)
                    .build());
                
                log.info("⚠️ [{}] Incohérence: FETCHED mais PDF existe", docId);
            } else {
                issues.add(Issue.builder()
                    .documentId(docId)
                    .type(Issue.IssueType.STUCK_IN_FETCHED)
                    .severity(Issue.IssueSeverity.HIGH)
                    .description("Document en FETCHED - PDF non téléchargé")
                    .currentStatus(document.getStatus().name())
                    .suggestedAction("Exécuter downloadJob pour ce document")
                    .detectedAt(LocalDateTime.now())
                    .autoFixable(true)
                    .build());
                
                log.info("📥 [{}] Bloqué en FETCHED", docId);
            }
        }
        
        // Documents en DOWNLOADED (doivent être extraits)
        if (document.getStatus() == LawDocument.ProcessingStatus.DOWNLOADED) {
            issues.add(Issue.builder()
                .documentId(docId)
                .type(Issue.IssueType.STUCK_IN_DOWNLOADED)
                .severity(Issue.IssueSeverity.HIGH)
                .description("Document en DOWNLOADED - extraction non effectuée")
                .currentStatus(document.getStatus().name())
                .suggestedAction("Exécuter pdfToJsonJob pour ce document")
                .detectedAt(LocalDateTime.now())
                .autoFixable(true)
                .build());
            
            log.info("📄 [{}] Bloqué en DOWNLOADED", docId);
        }
        
        // Documents en EXTRACTED (doivent être consolidés)
        if (document.getStatus() == LawDocument.ProcessingStatus.EXTRACTED) {
            issues.add(Issue.builder()
                .documentId(docId)
                .type(Issue.IssueType.STUCK_IN_EXTRACTED)
                .severity(Issue.IssueSeverity.MEDIUM)
                .description("Document en EXTRACTED - consolidation non effectuée")
                .currentStatus(document.getStatus().name())
                .suggestedAction("Exécuter consolidateJob")
                .detectedAt(LocalDateTime.now())
                .autoFixable(true)
                .build());
            
            log.info("💾 [{}] Bloqué en EXTRACTED", docId);
        }
        
        return issues;
    }
}
