package bj.gouv.sgg.fix.service;

import bj.gouv.sgg.fix.model.FixResult;
import bj.gouv.sgg.fix.model.Issue;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service de correction pour documents bloqués dans un statut.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatusFixService {
    
    private final LawDocumentRepository lawDocumentRepository;
    
    /**
     * Corrige un problème de statut en réinitialisant au statut précédent.
     * Permet au pipeline de re-traiter le document.
     */
    public FixResult fix(Issue issue) {
        String docId = issue.getDocumentId();
        
        if (!issue.isAutoFixable()) {
            return FixResult.builder()
                .documentId(docId)
                .issueType(issue.getType())
                .status(FixResult.FixStatus.SKIPPED)
                .action("Non auto-fixable")
                .details(issue.getDescription())
                .fixedAt(LocalDateTime.now())
                .retryCount(0)
                .build();
        }
        
        try {
            LawDocument document = lawDocumentRepository.findByTypeAndYearAndNumber(
                parseType(docId), parseYear(docId), parseNumber(docId)
            ).orElseThrow();
            
            // Cas spécial : incohérence FETCHED + PDF existe → avancer vers DOWNLOADED
            if (issue.getType() == Issue.IssueType.STATUS_INCONSISTENT) {
                document.setStatus(LawDocument.ProcessingStatus.DOWNLOADED);
                lawDocumentRepository.save(document);
                
                log.info("✅ [{}] Statut avancé: FETCHED → DOWNLOADED (PDF existe)", docId);
                
                return FixResult.builder()
                    .documentId(docId)
                    .issueType(issue.getType())
                    .status(FixResult.FixStatus.SUCCESS)
                    .action("Avancé FETCHED → DOWNLOADED")
                    .details("Statut mis à jour car PDF déjà téléchargé")
                    .fixedAt(LocalDateTime.now())
                    .retryCount(0)
                    .build();
            }
            
            // Cas spécial : PENDING ne peut pas être réinitialisé (déjà au début)
            // → Le laisser en PENDING pour que fetchJob le reprenne
            if (document.getStatus() == LawDocument.ProcessingStatus.PENDING) {
                log.info("⏭️ [{}] Document déjà en PENDING, aucune action nécessaire", docId);
                
                return FixResult.builder()
                    .documentId(docId)
                    .issueType(issue.getType())
                    .status(FixResult.FixStatus.SUCCESS)
                    .action("Maintenu en PENDING")
                    .details("Document prêt pour traitement par fetchJob")
                    .fixedAt(LocalDateTime.now())
                    .retryCount(0)
                    .build();
            }
            
            // Cas normal : réinitialisation vers statut précédent
            LawDocument.ProcessingStatus previousStatus = getPreviousStatus(document.getStatus());
            
            if (previousStatus == null) {
                return FixResult.builder()
                    .documentId(docId)
                    .issueType(issue.getType())
                    .status(FixResult.FixStatus.FAILED)
                    .action("Impossible de déterminer statut précédent")
                    .details("Current status: " + document.getStatus())
                    .fixedAt(LocalDateTime.now())
                    .retryCount(0)
                    .build();
            }
            
            document.setStatus(previousStatus);
            lawDocumentRepository.save(document);
            
            log.info("✅ [{}] Statut réinitialisé: {} → {}", 
                docId, issue.getCurrentStatus(), previousStatus);
            
            return FixResult.builder()
                .documentId(docId)
                .issueType(issue.getType())
                .status(FixResult.FixStatus.SUCCESS)
                .action(String.format("Réinitialisation %s → %s", 
                    issue.getCurrentStatus(), previousStatus))
                .details("Document prêt pour re-traitement")
                .fixedAt(LocalDateTime.now())
                .retryCount(1)
                .build();
                
        } catch (Exception e) {
            log.error("❌ [{}] Erreur correction statut: {}", docId, e.getMessage());
            return FixResult.builder()
                .documentId(docId)
                .issueType(issue.getType())
                .status(FixResult.FixStatus.FAILED)
                .action("Erreur lors de la correction")
                .details(e.getMessage())
                .fixedAt(LocalDateTime.now())
                .retryCount(0)
                .build();
        }
    }
    
    /**
     * Détermine le statut précédent dans le pipeline.
     */
    private LawDocument.ProcessingStatus getPreviousStatus(LawDocument.ProcessingStatus current) {
        return switch (current) {
            case FETCHED -> LawDocument.ProcessingStatus.PENDING;
            case DOWNLOADED -> LawDocument.ProcessingStatus.FETCHED;
            case EXTRACTED -> LawDocument.ProcessingStatus.DOWNLOADED;
            case CONSOLIDATED -> LawDocument.ProcessingStatus.EXTRACTED;
            default -> null;
        };
    }
    
    private String parseType(String documentId) {
        return documentId.split("-")[0];
    }
    
    private int parseYear(String documentId) {
        return Integer.parseInt(documentId.split("-")[1]);
    }
    
    private int parseNumber(String documentId) {
        return Integer.parseInt(documentId.split("-")[2]);
    }
}
