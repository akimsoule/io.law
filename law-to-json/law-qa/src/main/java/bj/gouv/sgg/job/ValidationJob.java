package bj.gouv.sgg.job;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.service.ValidationService;
import bj.gouv.sgg.service.ValidationService.ValidationResult;
import bj.gouv.sgg.service.impl.ValidationServiceImpl;
import bj.gouv.sgg.util.ErrorHandlingUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Job de validation qualité des documents extraits conforme à l'architecture law-consolidate.
 * 
 * <p>Délègue toute la logique métier à {@link ValidationService}.
 * 
 * <p>Workflow :
 * <ol>
 *   <li>Scan articles/{type}/ pour fichiers JSON</li>
 *   <li>Délègue validation à ValidationService</li>
 *   <li>Génère rapport consolidé</li>
 * </ol>
 * 
 * Gestion erreurs : non-bloquante, job continue en loggant les erreurs.
 * 
 * @see ValidationService
 * @author io.law
 * @since 1.0.0
 */
@Slf4j
public class ValidationJob {
    
    private final ValidationService validationService;
    private final AppConfig config;
    private final bj.gouv.sgg.service.DocumentStatusService statusService;
    
    public ValidationJob() {
        this.validationService = ValidationServiceImpl.getInstance();
        this.config = AppConfig.get();
        this.statusService = bj.gouv.sgg.service.DocumentStatusService.getInstance();
    }
    
    /**
     * Lance la validation pour un document spécifique (mode ciblé).
     * Thread-safe pour exécution concurrente.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    public synchronized void runDocument(String documentId) {
        log.info("🔄 Quality validation: documentId={}", documentId);
        
        ErrorHandlingUtils.executeVoid(() -> {
            // Parse documentId
            String[] parts = documentId.split("-");
            if (parts.length != 3) {
                log.warn("⚠️ Format invalide: {}", documentId);
                return;
            }
            
            String type = parts[0];
            
            // Chemins
            Path jsonPath = config.getStoragePath().resolve("articles").resolve(type).resolve(documentId + ".json");
            Path ocrPath = config.getStoragePath().resolve("ocr").resolve(type).resolve(documentId + ".txt");
            
            if (!Files.exists(jsonPath)) {
                log.warn("⚠️ Fichier non trouvé: {}", jsonPath);
                return;
            }
            
            // Déléguer validation au service
            ValidationResult result = validationService.validateDocument(jsonPath, ocrPath);
            
            // Mettre à jour le statut selon résultat validation
            if (result.isValid()) {
                statusService.updateStatus(documentId, bj.gouv.sgg.model.ProcessingStatus.VALIDATED);
                log.info("✅ Validated: {} (confidence: {:.2f})", documentId, result.getConfidence());
            } else {
                statusService.updateStatus(documentId, bj.gouv.sgg.model.ProcessingStatus.FAILED);
                log.warn("⚠️ Validation issues for {}: {} errors", documentId, result.getErrors().size());
                result.getErrors().stream().limit(3).forEach(err -> log.warn("  - {}", err));
            }
            
        }, "validateDocument", "documentId=" + documentId);
    }
    
    /**
     * Lance la validation pour tous les documents d'un type (loi/decret).
     * 
     * @param type Type de document ("loi" ou "decret")
     */
    public void run(String type) {
        log.info("🔄 Quality validation batch: type={}", type);
        
        // Déléguer validation au service
        List<ValidationResult> results = validationService.validateType(type);
        
        // Génération rapport
        generateReport(type, results);
    }
    
    /**
     * Génère rapport de validation.
     */
    private void generateReport(String type, List<ValidationResult> results) {
        long validCount = results.stream().filter(ValidationResult::isValid).count();
        long lowQualityCount = results.stream()
            .filter(r -> !r.isValid() && r.getConfidence() >= 0.3)
            .count();
        long invalidCount = results.stream()
            .filter(r -> r.getConfidence() < 0.3)
            .count();
        
        log.info("📊 Validation Report for type={}:", type);
        log.info("  ✅ Valid: {}", validCount);
        log.info("  ⚠️  Low quality: {}", lowQualityCount);
        log.info("  ❌ Invalid: {}", invalidCount);
        log.info("  📄 Total: {}", results.size());
        
        // Log premiers problèmes
        results.stream()
            .filter(r -> !r.isValid())
            .limit(10)
            .forEach(r -> {
                log.info("  ⚠️ {} : {} errors", r.getDocumentId(), r.getErrors().size());
                r.getErrors().stream().limit(3).forEach(err -> log.info("    - {}", err));
            });
    }
}
