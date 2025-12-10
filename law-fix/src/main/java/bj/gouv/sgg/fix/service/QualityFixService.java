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
 * Service de correction pour problèmes de qualité d'extraction.
 * Force la re-extraction avec --force pour améliorer la qualité.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QualityFixService {
    
    private final LawDocumentRepository lawDocumentRepository;
    
    /**
     * Corrige un problème de qualité en forçant la re-extraction.
     */
    public FixResult fix(Issue issue) {
        String docId = issue.getDocumentId();
        
        if (!issue.isAutoFixable()) {
            return FixResult.builder()
                .documentId(docId)
                .issueType(issue.getType())
                .status(FixResult.FixStatus.SKIPPED)
                .action("Non auto-fixable - Intervention manuelle requise")
                .details(issue.getDescription())
                .fixedAt(LocalDateTime.now())
                .retryCount(0)
                .build();
        }
        
        try {
            LawDocument document = lawDocumentRepository.findByTypeAndYearAndNumber(
                parseType(docId), parseYear(docId), parseNumber(docId)
            ).orElseThrow();
            
            switch (issue.getType()) {
                case LOW_CONFIDENCE, SEQUENCE_ISSUES, HIGH_UNRECOGNIZED_WORDS, MISSING_ARTICLES -> {
                    // Réinitialiser à DOWNLOADED pour re-extraction avec force
                    document.setStatus(LawDocument.ProcessingStatus.DOWNLOADED);
                    lawDocumentRepository.save(document);
                    
                    log.info("✅ [{}] Réinitialisé à DOWNLOADED pour re-extraction (qualité)", docId);
                    log.info("💡 [{}] Suggestion: Vérifier corrections.csv et word_non_recognize.txt", docId);
                    
                    return FixResult.builder()
                        .documentId(docId)
                        .issueType(issue.getType())
                        .status(FixResult.FixStatus.SUCCESS)
                        .action("Réinitialisé à DOWNLOADED")
                        .details("Re-extraction programmée. Vérifier corrections OCR avant relance.")
                        .fixedAt(LocalDateTime.now())
                        .retryCount(1)
                        .build();
                }
                
                default -> {
                    return FixResult.builder()
                        .documentId(docId)
                        .issueType(issue.getType())
                        .status(FixResult.FixStatus.FAILED)
                        .action("Type d'issue non géré")
                        .details(issue.getType().name())
                        .fixedAt(LocalDateTime.now())
                        .retryCount(0)
                        .build();
                }
            }
            
        } catch (Exception e) {
            log.error("❌ [{}] Erreur correction qualité: {}", docId, e.getMessage());
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
