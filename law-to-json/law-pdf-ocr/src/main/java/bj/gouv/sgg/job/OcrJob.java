package bj.gouv.sgg.job;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.service.JobService;
import bj.gouv.sgg.service.OcrService;
import bj.gouv.sgg.service.impl.OcrServiceImpl;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Job d'extraction OCR conforme à l'architecture law-consolidate.
 * 
 * Workflow :
 * 1. Scanne répertoire pdfs/{type}/
 * 2. Pour chaque PDF téléchargé
 * 3. Effectue l'OCR et sauvegarde dans ocr/{type}/
 * 
 * Ce job délègue toute la logique au OcrService.
 */
@Slf4j
public class OcrJob implements JobService {
    
    private final OcrService ocrService;
    private final AppConfig config;
    
    public OcrJob() {
        this.config = AppConfig.get();
        this.ocrService = OcrServiceImpl.getInstance();
    }
    
    /**
     * Lance l'OCR pour un document spécifique (mode ciblé).
     * Thread-safe pour exécution concurrente.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    @Override
    public synchronized void runDocument(String documentId) {
        log.info("🔄 OCR extraction: documentId={}", documentId);
        
        try {
            // Parse documentId
            String[] parts = documentId.split("-");
            if (parts.length != 3) {
                log.warn("⚠️ Format invalide: {}", documentId);
                return;
            }
            
            String type = parts[0];
            
            // Chemins
            Path pdfPath = config.getStoragePath().resolve("pdfs").resolve(type).resolve(documentId + ".pdf");
            Path ocrPath = config.getStoragePath().resolve("ocr").resolve(type).resolve(documentId + ".txt");
            
            File pdfFile = pdfPath.toFile();
            if (!pdfFile.exists()) {
                log.warn("⚠️ PDF non trouvé: {}", documentId);
                return;
            }
            
            File ocrFile = ocrPath.toFile();
            if (ocrFile.exists()) {
                log.debug("⏭️ OCR existe déjà: {}", documentId);
                return;
            }
            
            // Déléguer au service
            ocrService.performOcr(pdfFile, ocrFile);
            
        } catch (Exception e) {
            log.error("❌ OCR failed for {}: {}", documentId, e.getMessage(), e);
            // Continue, ne stop pas le job
        }
    }
    
    /**
     * Lance l'OCR pour un type de document.
     * 
     * @param type Type de document (loi/decret)
     */
    @Override
    public void runType(String type) {
        log.info("🔄 OCR extraction: type={}", type);
        if (config.getMaxDocumentsToExtract() > 0) {
            log.info("📊 Limite documents: {}", config.getMaxDocumentsToExtract());
        }
        
        try {
            Path pdfDir = config.getStoragePath().resolve("pdfs").resolve(type);
            
            if (!pdfDir.toFile().exists()) {
                log.warn("⚠️ Répertoire PDF non trouvé: {}", pdfDir);
                return;
            }
            
            File[] pdfFiles = pdfDir.toFile().listFiles((dir, name) -> {
                if (!name.endsWith(".pdf")) {
                    return false;
                }
                // Vérifier si l'OCR n'existe pas déjà
                String documentId = name.substring(0, name.length() - 4);
                Path ocrPath = config.getStoragePath().resolve("ocr").resolve(type).resolve(documentId + ".txt");
                return !ocrPath.toFile().exists();
            });
            
            if (pdfFiles == null || pdfFiles.length == 0) {
                log.warn("⚠️ Aucun PDF à traiter dans: {}", pdfDir);
                return;
            }
            
            // Trier par année décroissante puis numéro décroissant (plus récent au plus ancien)
            Arrays.sort(pdfFiles, (f1, f2) -> {
                try {
                    String name1 = f1.getName().substring(0, f1.getName().length() - 4);
                    String name2 = f2.getName().substring(0, f2.getName().length() - 4);
                    String[] parts1 = name1.split("-");
                    String[] parts2 = name2.split("-");
                    
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
                    log.debug("Unable to parse year/number from files");
                }
                return 0;
            });
            
            log.info("📂 Found {} PDFs to process in {}", pdfFiles.length, type);
            
            // Appliquer la limite maxDocumentsToExtract
            int maxToProcess = config.getMaxDocumentsToExtract();
            int totalFiles = pdfFiles.length;
            if (maxToProcess > 0 && totalFiles > maxToProcess) {
                log.info("🔢 Limitation à {} documents (sur {} disponibles)", maxToProcess, totalFiles);
            }
            
            int processed = 0;
            int failed = 0;
            
            for (File pdfFile : pdfFiles) {
                // Vérifier la limite maxDocumentsToExtract
                if (maxToProcess > 0 && processed >= maxToProcess) {
                    log.info("🛑 Limite de {} documents atteinte, arrêt du traitement", maxToProcess);
                    break;
                }
                
                String fileName = pdfFile.getName();
                String documentId = fileName.substring(0, fileName.length() - 4); // Remove .pdf
                
                Path ocrPath = config.getStoragePath().resolve("ocr").resolve(type).resolve(documentId + ".txt");
                
                try {
                    File ocrFile = ocrPath.toFile();
                    ocrService.performOcr(pdfFile, ocrFile);
                    processed++;
                    
                    // Log progression chaque 10 documents
                    if (processed % 10 == 0) {
                        log.info("📊 Progress: {} processed, {} failed", 
                                 processed, failed);
                    }
                    
                } catch (Exception e) {
                    log.error("❌ OCR failed for {}: {}", documentId, e.getMessage());
                    failed++;
                    // Continue avec le document suivant
                }
            }
            
            log.info("✅ OCR extraction completed: {} processed, {} failed", 
                     processed, failed);
            
        } catch (Exception e) {
            log.error("❌ OCR extraction failed for type {}: {}", type, e.getMessage(), e);
            // Ne pas propager l'exception
        }
    }
}
