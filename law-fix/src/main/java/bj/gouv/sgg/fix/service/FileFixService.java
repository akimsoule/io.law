package bj.gouv.sgg.fix.service;

import bj.gouv.sgg.fix.model.FixResult;
import bj.gouv.sgg.fix.model.Issue;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Service de correction pour fichiers manquants.
 * Réinitialise le statut pour forcer la régénération.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileFixService {
    
    private final LawDocumentRepository lawDocumentRepository;
    private final FileStorageService fileStorageService;
    
    /**
     * Corrige un problème de fichier manquant.
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
            
            switch (issue.getType()) {
                case MISSING_PDF -> {
                    // Réinitialiser à FETCHED pour re-télécharger
                    document.setStatus(LawDocument.ProcessingStatus.FETCHED);
                    lawDocumentRepository.save(document);
                    
                    log.info("✅ [{}] Réinitialisé à FETCHED pour re-téléchargement PDF", docId);
                    return buildSuccess(docId, issue.getType(), "Réinitialisé à FETCHED");
                }
                
                case MISSING_OCR, MISSING_JSON -> {
                    // Réinitialiser à DOWNLOADED pour re-extraction
                    document.setStatus(LawDocument.ProcessingStatus.DOWNLOADED);
                    lawDocumentRepository.save(document);
                    
                    log.info("✅ [{}] Réinitialisé à DOWNLOADED pour re-extraction", docId);
                    return buildSuccess(docId, issue.getType(), "Réinitialisé à DOWNLOADED");
                }
                
                case CORRUPTED_PDF -> {
                    // Supprimer PDF corrompu et réinitialiser
                    Path pdfPath = fileStorageService.pdfPath(document.getType(), docId);
                    if (Files.exists(pdfPath)) {
                        Files.delete(pdfPath);
                        log.info("🗑️  [{}] PDF corrompu supprimé", docId);
                    }
                    
                    document.setStatus(LawDocument.ProcessingStatus.FETCHED);
                    lawDocumentRepository.save(document);
                    
                    log.info("✅ [{}] PDF corrompu supprimé, réinitialisé à FETCHED", docId);
                    return buildSuccess(docId, issue.getType(), "PDF supprimé, réinitialisé à FETCHED");
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
            log.error("❌ [{}] Erreur correction fichier: {}", docId, e.getMessage());
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
    
    private FixResult buildSuccess(String docId, Issue.IssueType type, String action) {
        return FixResult.builder()
            .documentId(docId)
            .issueType(type)
            .status(FixResult.FixStatus.SUCCESS)
            .action(action)
            .details("Document prêt pour re-traitement")
            .fixedAt(LocalDateTime.now())
            .retryCount(1)
            .build();
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
