package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.exception.CorruptedPdfException;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.service.LawDocumentService;
import bj.gouv.sgg.service.OcrProcessingService;
import bj.gouv.sgg.service.OcrService;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implémentation du service de traitement OCR avec pattern Reader-Processor-Writer.
 * 
 * Architecture:
 * - READER: Récupère les fichiers PDF à traiter
 * - PROCESSOR: Effectue l'OCR via OcrService
 * - WRITER: Sauvegarde les fichiers texte
 */
@Slf4j
public class OcrProcessingServiceImpl implements OcrProcessingService {
    
    private static OcrProcessingServiceImpl instance;
    
    private final AppConfig config;
    private final OcrService ocrService;
    private final LawDocumentService lawDocumentService;
    
    private final List<LawDocumentEntity> documentEntities;
    private int successCount;
    private int failedCount;
    private int corruptedCount;
    
    private OcrProcessingServiceImpl() {
        this.config = AppConfig.get();
        this.ocrService = OcrServiceImpl.getInstance();
        this.lawDocumentService = new LawDocumentService();
        this.documentEntities = new ArrayList<>();
    }
    
    public static synchronized OcrProcessingServiceImpl getInstance() {
        if (instance == null) {
            instance = new OcrProcessingServiceImpl();
        }
        return instance;
    }
    
    @Override
    public void runType(String type) {
        log.info("🔄 OcrProcessingService: type={}", type);
        
        // Réinitialiser compteurs
        this.successCount = 0;
        this.failedCount = 0;
        this.corruptedCount = 0;
        this.documentEntities.clear();
        
        // ========== READER: Récupérer PDFs à traiter ==========
        List<File> pdfFiles = readPdfFiles(type);
        
        if (pdfFiles.isEmpty()) {
            log.warn("⚠️ Aucun PDF à traiter");
            return;
        }
        
        // ========== PROCESSOR: Effectuer OCR ==========
        log.info("📥 Processing {} PDFs...", pdfFiles.size());
        for (File pdfFile : pdfFiles) {
            processPdfFile(pdfFile, type);
        }
        
        // ========== WRITER: Sauvegarder entités ==========
        writeOcrResults(this.documentEntities);
        
        // ========== STATISTIQUES ==========
        log.info("✅ OcrProcessingService terminé: {} succès, {} échoués, {} corrompus", 
                 successCount, failedCount, corruptedCount);
    }
    
    @Override
    public void runDocument(String documentId) {
        log.info("🔄 OCR extraction: documentId={}", documentId);
        
        // Réinitialiser compteurs
        this.successCount = 0;
        this.failedCount = 0;
        this.corruptedCount = 0;
        this.documentEntities.clear();
        
        try {
            String[] parts = documentId.split("-");
            if (parts.length != 3) {
                log.warn("⚠️ Format invalide: {}", documentId);
                return;
            }
            
            String type = parts[0];
            Path pdfPath = getPdfPath(type, documentId);
            
            if (!pdfPath.toFile().exists()) {
                log.warn("⚠️ PDF non trouvé: {}", documentId);
                return;
            }
            
            Path ocrPath = getOcrPath(type, documentId);
            if (ocrPath.toFile().exists()) {
                log.debug("⏭️ OCR existe déjà: {}", documentId);
                return;
            }
            
            // PROCESSOR
            processPdfFile(pdfPath.toFile(), type);
            
            // WRITER
            writeOcrResults(this.documentEntities);
            
            log.info("✅ OcrProcessingService terminé: {} succès, {} échoués, {} corrompus", 
                     successCount, failedCount, corruptedCount);
            
        } catch (Exception e) {
            log.error("❌ Erreur OCR pour {}: {}", documentId, e.getMessage());
        }
    }
    
    // ========== READER ==========
    
    /**
     * READER: Récupère les fichiers PDF à traiter.
     * - Charge fichiers .pdf depuis pdfs/{type}/
     * - Filtre ceux dont l'OCR n'existe pas
     * - Trie par année et numéro (décroissant)
     * - Limite selon config.maxDocumentsToExtract
     * 
     * @return Liste des fichiers PDF à traiter
     */
    private List<File> readPdfFiles(String type) {
        log.info("📖 READER: Récupération fichiers PDF pour type '{}'...", type);
        
        Path pdfDir = config.getStoragePath().resolve("pdfs").resolve(type);
        if (!pdfDir.toFile().exists()) {
            log.warn("⚠️ Répertoire PDF non trouvé: {}", pdfDir);
            return List.of();
        }
        
        File[] files = pdfDir.toFile().listFiles((dir, name) -> {
            if (!name.endsWith(".pdf")) {
                return false;
            }
            // Vérifier si l'OCR n'existe pas déjà
            String documentId = name.substring(0, name.length() - 4);
            Path ocrPath = getOcrPath(type, documentId);
            return !ocrPath.toFile().exists();
        });
        
        if (files == null || files.length == 0) {
            log.warn("⚠️ Aucun fichier PDF à traiter dans: {}", pdfDir);
            return List.of();
        }
        
        // Trier par année et numéro (décroissant)
        sortByYearAndNumber(files);
        
        // Limiter selon configuration
        int maxToProcess = config.getMaxDocumentsToExtract();
        List<File> result = new ArrayList<>(Arrays.asList(files));
        
        if (maxToProcess > 0 && result.size() > maxToProcess) {
            result = result.subList(0, maxToProcess);
            log.info("📖 READER: Limitation à {} fichiers (sur {} disponibles)", maxToProcess, files.length);
        }
        
        log.info("📖 READER: {} fichiers PDF à traiter", result.size());
        return result;
    }
    
    // ========== PROCESSOR ==========
    
    /**
     * PROCESSOR: Effectue l'OCR sur un fichier PDF.
     * - Charge l'entité LawDocumentEntity
     * - Effectue l'OCR via OcrService
     * - Met à jour l'entité (status, errorMessage)
     * - Ajoute à la liste des résultats
     */
    private void processPdfFile(File pdfFile, String type) {
        String documentId = pdfFile.getName().replace(".pdf", "");
        log.debug("⚙️ PROCESSOR: {}", documentId);
        
        try {
            Path ocrPath = getOcrPath(type, documentId);
            File ocrFile = ocrPath.toFile();
            
            // Effectuer l'OCR
            ocrService.performOcr(pdfFile, ocrFile);
            
            // Charger ou créer l'entité
            LawDocumentEntity entity = lawDocumentService.findByDocumentId(documentId)
                .orElseGet(() -> LawDocumentEntity.createFromDocumentId(documentId, type));
            entity.setStatus(ProcessingStatus.OCRED);
            entity.setErrorMessage(null);
            documentEntities.add(entity);
            
            log.info("✅ OCR effectué: {}", documentId);
            successCount++;
            
            // Log progression chaque 10 documents
            if (successCount % 10 == 0) {
                log.info("📊 Progress: {} succès, {} échoués, {} corrompus", 
                         successCount, failedCount, corruptedCount);
            }
            
        } catch (CorruptedPdfException e) {
            log.error("🚨 PDF corrompu {}: {}", documentId, e.getMessage());
            
            LawDocumentEntity entity = lawDocumentService.findByDocumentId(documentId)
                .orElseGet(() -> LawDocumentEntity.createFromDocumentId(documentId, type));
            entity.setStatus(ProcessingStatus.FAILED_CORRUPTED);
            entity.setErrorMessage("PDF corrompu: " + e.getMessage());
            documentEntities.add(entity);
            
            corruptedCount++;
            
        } catch (Exception e) {
            log.error("❌ Erreur OCR {}: {}", documentId, e.getMessage());
            
            LawDocumentEntity entity = lawDocumentService.findByDocumentId(documentId)
                .orElseGet(() -> LawDocumentEntity.createFromDocumentId(documentId, type));
            entity.setStatus(ProcessingStatus.FAILED_OCR);
            entity.setErrorMessage(e.getMessage());
            documentEntities.add(entity);
            
            failedCount++;
        }
    }
    
    // ========== WRITER ==========
    
    /**
     * WRITER: Sauvegarde toutes les entités modifiées en batch.
     */
    private void writeOcrResults(List<LawDocumentEntity> entities) {
        if (entities.isEmpty()) {
            log.info("💾 WRITER: Aucune entité à sauvegarder");
            return;
        }
        
        log.info("💾 WRITER: Sauvegarde de {} entités...", entities.size());
        lawDocumentService.saveAll(entities);
        log.info("💾 WRITER: ✅ Sauvegarde terminée");
    }
    
    // ========== HELPERS ==========
    
    private void sortByYearAndNumber(File[] files) {
        Arrays.sort(files, (f1, f2) -> {
            try {
                String name1 = f1.getName().replace(".pdf", "");
                String name2 = f2.getName().replace(".pdf", "");
                String[] parts1 = name1.split("-");
                String[] parts2 = name2.split("-");
                
                if (parts1.length >= 3 && parts2.length >= 3) {
                    int year1 = Integer.parseInt(parts1[1]);
                    int year2 = Integer.parseInt(parts2[1]);
                    if (year1 != year2) return Integer.compare(year2, year1);
                    
                    int number1 = Integer.parseInt(parts1[2]);
                    int number2 = Integer.parseInt(parts2[2]);
                    return Integer.compare(number2, number1);
                }
            } catch (Exception e) {
                log.debug("Unable to parse year/number from files");
            }
            return 0;
        });
    }
    
    private Path getPdfPath(String type, String documentId) {
        return config.getStoragePath().resolve("pdfs").resolve(type).resolve(documentId + ".pdf");
    }
    
    private Path getOcrPath(String type, String documentId) {
        return config.getStoragePath().resolve("ocr").resolve(type).resolve(documentId + ".txt");
    }
}
