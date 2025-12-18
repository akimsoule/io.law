package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.job.ArticleExtractionJob;
import bj.gouv.sgg.job.OcrJob;
import bj.gouv.sgg.model.JsonResult;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.service.FileStorageService;
import bj.gouv.sgg.service.OcrTransformer;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

/**
 * Implémentation de OcrTransformer qui délègue aux modules existants.
 * 
 * <p>Workflow :
 * <pre>
 * 1. law-pdf-ocr : PDF → Texte OCR brut (OcrJob)
 * 2. law-ocr-json : Texte OCR → JSON structuré (ArticleExtractionJob)
 * </pre>
 */
@Slf4j
public class OcrTransformerImpl implements OcrTransformer {
    
    private static OcrTransformerImpl instance;
    
    private final OcrJob ocrJob;
    private final ArticleExtractionJob articleExtractionJob;
    private final FileStorageService fileStorageService;
    private final AppConfig config;
    
    private OcrTransformerImpl(
            OcrJob ocrJob,
            ArticleExtractionJob articleExtractionJob,
            FileStorageService fileStorageService,
            AppConfig config) {
        this.ocrJob = ocrJob;
        this.articleExtractionJob = articleExtractionJob;
        this.fileStorageService = fileStorageService;
        this.config = config;
    }
    
    public static OcrTransformerImpl getInstance() {
        if (instance == null) {
            AppConfig config = AppConfig.get();
            instance = new OcrTransformerImpl(
                new OcrJob(),
                new ArticleExtractionJob(),
                new FileStorageService(config),
                config
            );
        }
        return instance;
    }
    
    @Override
    public JsonResult transform(LawDocumentEntity document, Path pdfPath) throws IAException {
        String documentId = document.getDocumentId();
        
        try {
            // ÉTAPE 1 : Extraction OCR (PDF → Texte)
            log.debug("🔹 [{}] Étape 1/2: Extraction OCR", documentId);
            ocrJob.runDocument(documentId);
            
            // Vérifier que l'OCR a été créé
            if (!fileStorageService.ocrExists(document.getType(), documentId)) {
                throw new IAException("[" + documentId + "] Fichier OCR non créé après extraction");
            }
            
            // ÉTAPE 2 : Extraction Articles (Texte → JSON)
            log.debug("🔹 [{}] Étape 2/2: Extraction articles", documentId);
            articleExtractionJob.runDocument(documentId);
            
            // Vérifier que le JSON a été créé
            if (!fileStorageService.jsonExists(document.getType(), documentId)) {
                throw new IAException("[" + documentId + "] Fichier JSON non créé après extraction");
            }
            
            // Lire le JSON généré
            String jsonContent = fileStorageService.readJson(document.getType(), documentId);
            
            // Calculer la confiance (OCR de base : ~0.7)
            double confidence = 0.70;
            String source = "OCR:CSV";
            
            log.debug("✅ [{}] Transformation OCR complète: confiance {}", documentId, confidence);
            return new JsonResult(jsonContent, confidence, source);
            
        } catch (Exception e) {
            log.error("❌ [{}] Échec transformation OCR: {}", documentId, e.getMessage());
            throw new IAException("Transformation OCR échouée: " + e.getMessage(), e);
        }
    }
}
