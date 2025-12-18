package bj.gouv.sgg.job;

import bj.gouv.sgg.service.OcrProcessingService;
import bj.gouv.sgg.service.impl.OcrProcessingServiceImpl;
import lombok.extern.slf4j.Slf4j;

/**
 * Job d'extraction OCR depuis PDFs.
 * Délègue toute la logique au OcrProcessingService.
 * 
 * @see OcrProcessingServiceImpl
 */
@Slf4j
public class OcrJob {
    
    private final OcrProcessingService ocrProcessingService;
    
    public OcrJob() {
        this.ocrProcessingService = OcrProcessingServiceImpl.getInstance();
    }
    
    /**
     * Effectue l'OCR sur un document spécifique (mode ciblé).
     * Thread-safe pour exécution concurrente.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    public synchronized void runDocument(String documentId) {
        ocrProcessingService.runDocument(documentId);
    }
    
    /**
     * Effectue l'OCR sur tous les PDFs d'un type.
     * 
     * @param type Type de document (loi/decret)
     */
    public void run(String type) {
        ocrProcessingService.runType(type);
    }
    
    /**
     * Ferme les ressources.
     */
    public void shutdown() {
        log.info("🛑 OcrJob shutdown");
    }
}
