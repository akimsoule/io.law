package bj.gouv.sgg.job;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.service.IAService;
import bj.gouv.sgg.service.impl.IAServiceImpl;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Job d'extraction IA conforme à l'architecture law-consolidate.
 * 
 * <p>Délègue toute la logique métier à {@link IAService}.
 * 
 * <p>Workflow :
 * <ol>
 *   <li>Scan ocr/{type}/ pour fichiers OCR</li>
 *   <li>Correction OCR via IA (IAService.correctOcrText)</li>
 *   <li>Extraction JSON via IA (IAService.extractJsonFromOcr)</li>
 *   <li>Sauvegarde JSON dans articles/{type}/</li>
 * </ol>
 * 
 * Gestion erreurs : non-bloquante, job continue en loggant les erreurs.
 * 
 * @see IAService
 * @author io.law
 * @since 1.0.0
 */
@Slf4j
public class IAExtractionJob {
    
    private final IAService iaService;
    private final AppConfig config;
    
    public IAExtractionJob() {
        this.iaService = IAServiceImpl.getInstance();
        this.config = AppConfig.get();
    }
    
    /**
     * Lance l'extraction IA pour un document spécifique (mode ciblé).
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    public synchronized void runDocument(String documentId) {
        log.info("🤖 IA extraction: documentId={}", documentId);
        
        if (!iaService.isAvailable()) {
            log.warn("⚠️ IA service not available, skipping IA extraction");
            return;
        }
        
        try {
            // Parse documentId
            String[] parts = documentId.split("-");
            if (parts.length != 3) {
                log.warn("⚠️ Format invalide: {}", documentId);
                return;
            }
            
            String type = parts[0];
            
            // Chemins
            Path ocrPath = config.getStoragePath().resolve("ocr").resolve(type).resolve(documentId + ".txt");
            Path jsonPath = config.getStoragePath().resolve("articles").resolve(type).resolve(documentId + ".json");
            
            if (!Files.exists(ocrPath)) {
                log.warn("⚠️ Fichier OCR non trouvé: {}", ocrPath);
                return;
            }
            
            try {
                // Lire OCR
                String ocrText = Files.readString(ocrPath);
                
                // Correction OCR via IA
                String correctedText = iaService.correctOcrText(ocrText, "Corriger les erreurs OCR");
                
                // Extraction JSON via IA
                JsonObject jsonResult = iaService.extractJsonFromOcr(correctedText);
                
                // Sauvegarder JSON
                Files.createDirectories(jsonPath.getParent());
                Files.writeString(jsonPath, jsonResult.toString());
                
                log.info("✅ IA extraction completed: {} (source: {})", 
                         documentId, iaService.getSourceName());
                         
            } catch (Exception e) {
                log.error("❌ Échec extraction IA pour {}: {}", documentId, e.getMessage());
            }
        } catch (Exception e) {
            log.error("❌ Error in IA extraction: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Lance l'extraction IA pour tous les documents d'un type (loi/decret).
     * 
     * @param type Type de document ("loi" ou "decret")
     */
    public void run(String type) {
        log.info("🤖 IA extraction batch: type={}", type);
        
        if (!iaService.isAvailable()) {
            log.warn("⚠️ IA service not available, skipping IA extraction");
            return;
        }
        
        Path ocrDir = config.getStoragePath().resolve("ocr").resolve(type);
        
        if (!Files.exists(ocrDir)) {
            log.warn("⚠️ Répertoire OCR non trouvé: {}", ocrDir);
            return;
        }
        
        // Compteurs
        int[] counts = {0, 0}; // [processed, failed]
        
        try {
            try (Stream<Path> paths = Files.walk(ocrDir, 1)) {
                paths.filter(p -> p.toString().endsWith(".txt"))
                     .forEach(ocrPath -> {
                         String documentId = ocrPath.getFileName().toString().replace(".txt", "");
                         
                         try {
                             runDocument(documentId);
                             counts[0]++;
                         } catch (Exception e) {
                             log.error("❌ Error processing {}: {}", documentId, e.getMessage());
                             counts[1]++;
                         }
                     });
            }
        } catch (Exception e) {
            log.error("❌ Error scanning OCR files: {}", e.getMessage(), e);
        }
        
        // Rapport
        log.info("📊 IA Extraction Report for type={}:", type);
        log.info("  ✅ Processed: {}", counts[0]);
        log.info("  ❌ Failed: {}", counts[1]);
    }

}
