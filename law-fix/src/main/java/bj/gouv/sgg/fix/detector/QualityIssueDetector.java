package bj.gouv.sgg.fix.detector;

import bj.gouv.sgg.fix.model.Issue;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.service.FileStorageService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Détecteur de problèmes de qualité d'extraction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QualityIssueDetector {
    
    private final FileStorageService fileStorageService;
    private final Gson gson;
    
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.3;
    private static final double HIGH_UNRECOGNIZED_RATE_THRESHOLD = 0.5;
    
    /**
     * Détecte les problèmes de qualité pour un document.
     */
    public List<Issue> detect(LawDocument document) {
        List<Issue> issues = new ArrayList<>();
        String docId = document.getDocumentId();
        
        // Vérifier uniquement si document extrait
        if (document.getStatus() != LawDocument.ProcessingStatus.EXTRACTED &&
            document.getStatus() != LawDocument.ProcessingStatus.CONSOLIDATED) {
            return issues;
        }
        
        // Charger le JSON d'extraction
        Path jsonPath = fileStorageService.jsonPath(document.getType(), docId);
        if (!Files.exists(jsonPath)) {
            return issues; // Sera détecté par FileIssueDetector
        }
        
        try {
            String jsonContent = Files.readString(jsonPath);
            JsonObject json = gson.fromJson(jsonContent, JsonObject.class);
            
            if (json.has("_metadata")) {
                JsonObject metadata = json.getAsJsonObject("_metadata");
                checkConfidence(issues, docId, metadata, document.getStatus().name());
                checkSequenceIssues(issues, docId, metadata, document.getStatus().name());
                checkUnrecognizedWords(issues, docId, metadata, document.getStatus().name());
            }
            
            checkMissingArticles(issues, docId, json, document.getStatus().name());
            
        } catch (Exception e) {
            log.error("❌ [{}] Erreur lecture JSON qualité: {}", docId, e.getMessage());
        }
        
        return issues;
    }
    
    private void checkConfidence(List<Issue> issues, String docId, JsonObject metadata, String status) {
        if (!metadata.has("confidence")) {
            return;
        }
        
        double confidence = metadata.get("confidence").getAsDouble();
        if (confidence < LOW_CONFIDENCE_THRESHOLD) {
            issues.add(Issue.builder()
                .documentId(docId)
                .type(Issue.IssueType.LOW_CONFIDENCE)
                .severity(Issue.IssueSeverity.HIGH)
                .description(String.format("Confiance très faible: %.2f (seuil: %.2f)", 
                    confidence, LOW_CONFIDENCE_THRESHOLD))
                .currentStatus(status)
                .suggestedAction("Re-extraire avec force ou vérifier corrections OCR")
                .detectedAt(LocalDateTime.now())
                .autoFixable(true)
                .build());
            
            log.warn("⚠️  [{}] Confiance faible: {}", docId, confidence);
        }
    }
    
    private void checkSequenceIssues(List<Issue> issues, String docId, JsonObject metadata, String status) {
        if (!metadata.has("sequenceIssues")) {
            return;
        }
        
        JsonObject sequenceIssues = metadata.getAsJsonObject("sequenceIssues");
        int totalIssues = sequenceIssues.get("gaps").getAsInt() +
                         sequenceIssues.get("duplicates").getAsInt() +
                         sequenceIssues.get("outOfOrder").getAsInt();
        
        if (totalIssues > 0) {
            issues.add(Issue.builder()
                .documentId(docId)
                .type(Issue.IssueType.SEQUENCE_ISSUES)
                .severity(Issue.IssueSeverity.MEDIUM)
                .description(String.format("Problèmes séquence: %d gaps, %d duplicates, %d inversions",
                    sequenceIssues.get("gaps").getAsInt(),
                    sequenceIssues.get("duplicates").getAsInt(),
                    sequenceIssues.get("outOfOrder").getAsInt()))
                .currentStatus(status)
                .suggestedAction("Vérifier OCR et ajouter corrections CSV")
                .detectedAt(LocalDateTime.now())
                .autoFixable(true)
                .build());
            
            log.warn("⚠️  [{}] Problèmes de séquence détectés: {}", docId, totalIssues);
        }
    }
    
    private void checkUnrecognizedWords(List<Issue> issues, String docId, JsonObject metadata, String status) {
        if (!metadata.has("unrecognizedWordsRate")) {
            return;
        }
        
        double rate = metadata.get("unrecognizedWordsRate").getAsDouble();
        if (rate > HIGH_UNRECOGNIZED_RATE_THRESHOLD) {
            issues.add(Issue.builder()
                .documentId(docId)
                .type(Issue.IssueType.HIGH_UNRECOGNIZED_WORDS)
                .severity(Issue.IssueSeverity.MEDIUM)
                .description(String.format("Taux mots non reconnus élevé: %.1f%% (seuil: %.1f%%)",
                    rate * 100, HIGH_UNRECOGNIZED_RATE_THRESHOLD * 100))
                .currentStatus(status)
                .suggestedAction("Ajouter corrections OCR depuis data/word_non_recognize.txt")
                .detectedAt(LocalDateTime.now())
                .autoFixable(true)
                .build());
            
            log.warn("⚠️  [{}] Taux mots non reconnus élevé: {:.1f}%", docId, rate * 100);
        }
    }
    
    private void checkMissingArticles(List<Issue> issues, String docId, JsonObject json, String status) {
        if (!json.has("articles") || json.getAsJsonArray("articles").isEmpty()) {
            issues.add(Issue.builder()
                .documentId(docId)
                .type(Issue.IssueType.MISSING_ARTICLES)
                .severity(Issue.IssueSeverity.CRITICAL)
                .description("Aucun article extrait dans le JSON")
                .currentStatus(status)
                .suggestedAction("Re-extraire avec amélioration patterns ou corrections OCR")
                .detectedAt(LocalDateTime.now())
                .autoFixable(true)
                .build());
            
            log.error("🔴 [{}] Aucun article extrait", docId);
        }
    }
}
