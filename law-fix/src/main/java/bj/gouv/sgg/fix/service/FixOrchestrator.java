package bj.gouv.sgg.fix.service;

import bj.gouv.sgg.fix.detector.FileIssueDetector;
import bj.gouv.sgg.fix.detector.QualityIssueDetector;
import bj.gouv.sgg.fix.detector.StatusIssueDetector;
import bj.gouv.sgg.fix.model.FixResult;
import bj.gouv.sgg.fix.model.Issue;
import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrateur de détection et correction d'issues.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FixOrchestrator {
    
    private final StatusIssueDetector statusIssueDetector;
    private final FileIssueDetector fileIssueDetector;
    private final QualityIssueDetector qualityIssueDetector;
    
    private final StatusFixService statusFixService;
    private final FileFixService fileFixService;
    private final QualityFixService qualityFixService;
    
    /**
     * Détecte tous les problèmes pour un document.
     */
    public List<Issue> detectAllIssues(LawDocument document) {
        List<Issue> allIssues = new ArrayList<>();
        
        // Détecter problèmes de statut
        allIssues.addAll(statusIssueDetector.detect(document));
        
        // Détecter problèmes de fichiers
        allIssues.addAll(fileIssueDetector.detect(document));
        
        // Détecter problèmes de qualité
        allIssues.addAll(qualityIssueDetector.detect(document));
        
        return allIssues;
    }
    
    /**
     * Corrige un problème détecté.
     */
    public FixResult fixIssue(Issue issue) {
        log.info("🔧 [{}] Tentative correction: {}", issue.getDocumentId(), issue.getType());
        
        return switch (issue.getType()) {
            // Problèmes de statut
            case STUCK_IN_PENDING, STUCK_IN_FETCHED, 
                 STUCK_IN_DOWNLOADED, STUCK_IN_EXTRACTED -> 
                statusFixService.fix(issue);
            
            // Problèmes de fichiers
            case MISSING_PDF, MISSING_OCR, MISSING_JSON, CORRUPTED_PDF -> 
                fileFixService.fix(issue);
            
            // Problèmes de qualité
            case LOW_CONFIDENCE, SEQUENCE_ISSUES, 
                 HIGH_UNRECOGNIZED_WORDS, MISSING_ARTICLES -> 
                qualityFixService.fix(issue);
            
            // Problèmes non auto-fixables
            default -> FixResult.builder()
                .documentId(issue.getDocumentId())
                .issueType(issue.getType())
                .status(FixResult.FixStatus.SKIPPED)
                .action("Type non géré automatiquement")
                .details(issue.getDescription())
                .build();
        };
    }
    
    /**
     * Détecte et corrige tous les problèmes pour un document.
     */
    public List<FixResult> detectAndFixAll(LawDocument document) {
        List<Issue> issues = detectAllIssues(document);
        List<FixResult> results = new ArrayList<>();
        
        if (issues.isEmpty()) {
            log.debug("✅ [{}] Aucun problème détecté", document.getDocumentId());
            return results;
        }
        
        log.info("📋 [{}] {} problème(s) détecté(s)", document.getDocumentId(), issues.size());
        
        // Prioriser : CRITICAL > HIGH > MEDIUM > LOW
        issues.stream()
            .sorted((i1, i2) -> i2.getSeverity().compareTo(i1.getSeverity()))
            .forEach(issue -> {
                FixResult result = fixIssue(issue);
                results.add(result);
                
                // Log résultat
                if (result.getStatus() == FixResult.FixStatus.SUCCESS) {
                    log.info("✅ [{}] Corrigé: {} - {}", 
                        issue.getDocumentId(), issue.getType(), result.getAction());
                } else if (result.getStatus() == FixResult.FixStatus.FAILED) {
                    log.error("❌ [{}] Échec correction: {} - {}", 
                        issue.getDocumentId(), issue.getType(), result.getDetails());
                }
            });
        
        return results;
    }
}
