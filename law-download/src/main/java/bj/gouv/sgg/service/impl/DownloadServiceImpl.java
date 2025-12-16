package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.exception.DownloadException;
import bj.gouv.sgg.model.DocumentRecord;
import bj.gouv.sgg.model.ProcessingStatus;
import bj.gouv.sgg.service.DocumentService;
import bj.gouv.sgg.service.DownloadService;
import bj.gouv.sgg.service.FileStorageService;
import bj.gouv.sgg.service.PdfDownloadService;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Implémentation du service de téléchargement.
 */
@Slf4j
public class DownloadServiceImpl implements DownloadService {
    
    private static DownloadServiceImpl instance;
    
    private final AppConfig config;
    private final DocumentService documentService;
    private final FileStorageService fileStorageService;
    private final PdfDownloadService pdfDownloadService;
    
    private DownloadServiceImpl() {
        this.config = AppConfig.get();
        this.documentService = new DocumentService();
        this.fileStorageService = new FileStorageService();
        this.pdfDownloadService = new PdfDownloadService();
    }
    
    public static synchronized DownloadServiceImpl getInstance() {
        if (instance == null) {
            instance = new DownloadServiceImpl();
        }
        return instance;
    }
    
    @Override
    public void runDocument(String documentId) {
        log.info("⬇️  download: documentId={}", documentId);
        
        try {
            // Chercher le document
            Optional<DocumentRecord> docOpt = documentService.findByDocumentId(documentId);
            if (docOpt.isEmpty()) {
                log.warn("⚠️ Document non trouvé: {}", documentId);
                return;
            }
            
            DocumentRecord doc = docOpt.get();
            
            // Vérifier statut
            if (doc.getStatus() != ProcessingStatus.FETCHED) {
                log.warn("⚠️ Statut incorrect: {} (attendu: FETCHED)", doc.getStatus());
                return;
            }
            
            // Vérifier si déjà téléchargé
            Path pdfPath = fileStorageService.pdfPath(doc.getType(), documentId);
            if (Files.exists(pdfPath)) {
                log.debug("⏭️ Déjà téléchargé: {}", documentId);
                doc.setStatus(ProcessingStatus.DOWNLOADED);
                documentService.save(doc);
                return;
            }
            
            // Télécharger
            String hash = pdfDownloadService.downloadPdf(
                doc.getType(), 
                doc.getYear(), 
                doc.getNumber(), 
                pdfPath
            );
            
            doc.setStatus(ProcessingStatus.DOWNLOADED);
            documentService.save(doc);
            log.info("✅ Téléchargé: {} (hash: {})", documentId, hash.substring(0, 8));
            
        } catch (Exception e) {
            log.error("❌ Erreur download {}: {}", documentId, e.getMessage());
            try {
                Optional<DocumentRecord> docOpt = documentService.findByDocumentId(documentId);
                if (docOpt.isPresent()) {
                    DocumentRecord doc = docOpt.get();
                    doc.setStatus(ProcessingStatus.FAILED);
                    documentService.save(doc);
                }
            } catch (Exception saveEx) {
                log.error("❌ Erreur sauvegarde statut: {}", saveEx.getMessage());
            }
        }
    }
    
    @Override
    public void runType(String type) {
        log.info("⬇️  DownloadJob: type={}", type);
        
        // Récupérer documents FETCHED
        List<DocumentRecord> documents = documentService.findByTypeAndStatus(type, ProcessingStatus.FETCHED);
        
        if (documents.isEmpty()) {
            log.warn("⚠️ Aucun document FETCHED à télécharger");
            return;
        }
        
        log.info("📄 {} documents à télécharger", documents.size());
        
        // Télécharger chaque document
        int success = 0;
        int failed = 0;
        
        for (DocumentRecord doc : documents) {
            try {
                runDocument(doc.getDocumentId());
                success++;
            } catch (Exception e) {
                log.error("❌ Erreur: {}", e.getMessage());
                failed++;
            }
        }
        
        log.info("✅ DownloadJob terminé: {} succès, {} échecs", success, failed);
    }
}
