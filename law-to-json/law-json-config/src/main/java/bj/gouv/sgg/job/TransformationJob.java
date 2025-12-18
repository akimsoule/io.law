package bj.gouv.sgg.job;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.model.JsonResult;
import bj.gouv.sgg.service.FileStorageService;
import bj.gouv.sgg.service.JobService;
import bj.gouv.sgg.service.LawTransformationService;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Job de transformation PDF → JSON avec stratégie de fallback intelligente.
 * 
 * <p>Orchestre le pipeline complet :
 * <pre>
 * 1. OCR + Corrections CSV (law-pdf-ocr + law-ocr-json)
 * 2. Vérification qualité
 * 3. AI Correction OCR si nécessaire
 * 4. AI Correction JSON si nécessaire
 * 5. AI Extraction complète (fallback final)
 * </pre>
 * 
 * <p><b>Architecture standard</b> :
 * <ul>
 *   <li>{@code runDocument(String)} : Traite un document spécifique</li>
 *   <li>{@code runType(String)} : Traite tous les documents d'un type</li>
 * </ul>
 */
@Slf4j
public class TransformationJob implements JobService {
    
    private final LawTransformationService transformationService;
    private final FileStorageService fileStorageService;
    private final AppConfig config;
    private final Gson gson;
    
    private static final String LOG_SEPARATOR = "═══════════════════════════════════════════════════════════════════════════";
    
    public TransformationJob(
            LawTransformationService transformationService,
            FileStorageService fileStorageService,
            AppConfig config,
            Gson gson) {
        this.transformationService = transformationService;
        this.fileStorageService = fileStorageService;
        this.config = config;
        this.gson = gson;
    }
    
    /**
     * Traite un document spécifique (mode ciblé).
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     */
    @Override
    public void runDocument(String documentId) {
        log.info(LOG_SEPARATOR);
        log.info("🎯 Transformation ciblée: {}", documentId);
        log.info(LOG_SEPARATOR);
        
        try {
            // Parser l'ID pour extraire type, année, numéro
            String[] parts = documentId.split("-");
            if (parts.length != 3) {
                log.error("❌ Format ID invalide: {} (attendu: type-année-numéro)", documentId);
                return;
            }
            
            String type = parts[0];
            int year = Integer.parseInt(parts[1]);
            int number = Integer.parseInt(parts[2]);
            
            // Vérifier que le PDF existe
            if (!fileStorageService.pdfExists(type, documentId)) {
                log.error("❌ [{}] PDF introuvable, skip", documentId);
                return;
            }
            
            // Créer le document
            bj.gouv.sgg.entity.LawDocumentEntity document = 
                bj.gouv.sgg.entity.LawDocumentEntity.create(type, year, String.valueOf(number));
            
            // Transformer
            Path pdfPath = fileStorageService.getPdfPath(type, documentId);
            JsonResult result = transformationService.transform(document, pdfPath);
            
            // Sauvegarder le JSON
            fileStorageService.writeJson(type, documentId, result.getJson());
            
            log.info("✅ [{}] Transformation réussie: {} articles, confiance {}, source {}", 
                     documentId, extractArticleCount(result), result.getConfidence(), result.getSource());
            
        } catch (IAException e) {
            log.error("❌ [{}] Échec transformation: {}", documentId, e.getMessage());
        } catch (Exception e) {
            log.error("❌ [{}] Erreur inattendue: {}", documentId, e.getMessage(), e);
        }
        
        log.info(LOG_SEPARATOR);
    }
    
    /**
     * Traite tous les documents d'un type (mode batch).
     * 
     * @param type Type de document (loi ou decret)
     */
    @Override
    public void runType(String type) {
        log.info(LOG_SEPARATOR);
        log.info("🚀 Transformation batch: type={}", type);
        if (config.getMaxDocumentsToExtract() > 0) {
            log.info("📊 Limite documents: {}", config.getMaxDocumentsToExtract());
        }
        log.info(LOG_SEPARATOR);
        
        // Récupérer tous les PDFs du type
        List<String> documentIds = listPdfs(type);
        log.info("📄 [{}] {} PDFs trouvés", type, documentIds.size());
        
        if (documentIds.isEmpty()) {
            log.warn("⚠️ [{}] Aucun PDF à traiter", type);
            return;
        }
        
        // Trier par année décroissante puis numéro décroissant (plus récent au plus ancien)
        Collections.sort(documentIds, (docId1, docId2) -> {
            try {
                // Format: type-year-number (ex: loi-2024-15)
                String[] parts1 = docId1.split("-");
                String[] parts2 = docId2.split("-");
                
                if (parts1.length >= 3 && parts2.length >= 3) {
                    int year1 = Integer.parseInt(parts1[1]);
                    int year2 = Integer.parseInt(parts2[1]);
                    int number1 = Integer.parseInt(parts1[2]);
                    int number2 = Integer.parseInt(parts2[2]);
                    
                    // Tri par année décroissante
                    if (year1 != year2) {
                        return Integer.compare(year2, year1);
                    }
                    // Si même année, tri par numéro décroissant
                    return Integer.compare(number2, number1);
                }
            } catch (Exception e) {
                log.debug("Unable to parse year/number from: {}", docId1);
            }
            return 0;
        });
        
        // Appliquer la limite maxDocumentsToExtract si définie
        if (config.getMaxDocumentsToExtract() > 0 && documentIds.size() > config.getMaxDocumentsToExtract()) {
            log.info("🔢 Limitation à {} documents (sur {} disponibles)", 
                     config.getMaxDocumentsToExtract(), documentIds.size());
            documentIds = documentIds.subList(0, config.getMaxDocumentsToExtract());
        }
        
        // Statistiques
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;
        
        // Traiter chaque document
        for (int i = 0; i < documentIds.size(); i++) {
            String documentId = documentIds.get(i);
            log.info("▶️  [{}/{}] Traitement: {}", i + 1, documentIds.size(), documentId);
            
            try {
                // Vérifier si JSON existe déjà
                if (fileStorageService.jsonExists(type, documentId)) {
                    log.info("⏭️ [{}] JSON existe déjà, skip", documentId);
                    skippedCount++;
                    continue;
                }
                
                // Parser l'ID
                String[] parts = documentId.split("-");
                if (parts.length != 3) {
                    log.error("❌ [{}] Format ID invalide, skip", documentId);
                    failureCount++;
                    continue;
                }
                
                int year = Integer.parseInt(parts[1]);
                int number = Integer.parseInt(parts[2]);
                
                // Créer le document
                bj.gouv.sgg.entity.LawDocumentEntity document = 
                    bj.gouv.sgg.entity.LawDocumentEntity.create(type, year, String.valueOf(number));
                
                // Transformer
                Path pdfPath = fileStorageService.getPdfPath(type, documentId);
                JsonResult result = transformationService.transform(document, pdfPath);
                
                // Sauvegarder le JSON
                fileStorageService.writeJson(type, documentId, result.getJson());
                
                successCount++;
                log.info("✅ [{}] Succès ({}/{} articles, confiance {}, source {})", 
                         documentId, extractArticleCount(result), result.getConfidence(), result.getSource());
                
            } catch (IAException e) {
                failureCount++;
                log.error("❌ [{}] Échec: {}", documentId, e.getMessage());
            } catch (Exception e) {
                failureCount++;
                log.error("❌ [{}] Erreur inattendue: {}", documentId, e.getMessage());
            }
        }
        
        // Rapport final
        log.info(LOG_SEPARATOR);
        log.info("📊 Rapport final: type={}", type);
        log.info("   ✅ Succès:   {}/{} ({} %)", successCount, documentIds.size(), 
                 calculatePercentage(successCount, documentIds.size()));
        log.info("   ❌ Échecs:   {}/{} ({} %)", failureCount, documentIds.size(), 
                 calculatePercentage(failureCount, documentIds.size()));
        log.info("   ⏭️ Skipped: {}/{} ({} %)", skippedCount, documentIds.size(), 
                 calculatePercentage(skippedCount, documentIds.size()));
        log.info(LOG_SEPARATOR);
    }
    
    /**
     * Liste tous les PDFs d'un type.
     */
    private List<String> listPdfs(String type) {
        List<String> documentIds = new ArrayList<>();
        
        Path pdfDir = config.getPdfDir().resolve(type);
        if (!Files.exists(pdfDir)) {
            log.warn("⚠️ Répertoire PDF introuvable: {}", pdfDir);
            return documentIds;
        }
        
        try (Stream<Path> paths = Files.walk(pdfDir, 1)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".pdf"))
                 .forEach(p -> {
                     String filename = p.getFileName().toString();
                     String documentId = filename.replace(".pdf", "");
                     documentIds.add(documentId);
                 });
        } catch (IOException e) {
            log.error("❌ Erreur lecture répertoire PDFs: {}", e.getMessage());
        }
        
        return documentIds;
    }
    
    /**
     * Extrait le nombre d'articles depuis le JSON.
     */
    private int extractArticleCount(JsonResult result) {
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(result.getJson()).getAsJsonObject();
            if (root.has("articles")) {
                return root.getAsJsonArray("articles").size();
            }
        } catch (Exception e) {
            log.warn("⚠️ Impossible d'extraire le nombre d'articles: {}", e.getMessage());
        }
        return 0;
    }
    
    /**
     * Calcule un pourcentage.
     */
    private int calculatePercentage(int value, int total) {
        if (total == 0) return 0;
        return (int) ((value * 100.0) / total);
    }
}
