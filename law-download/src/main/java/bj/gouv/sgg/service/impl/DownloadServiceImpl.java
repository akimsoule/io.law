package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.model.ProcessingStatus;
import bj.gouv.sgg.service.LawDocumentService;
import bj.gouv.sgg.service.DownloadService;
import bj.gouv.sgg.service.FileStorageService;
import bj.gouv.sgg.service.PdfDownloadService;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implémentation du service de téléchargement avec pattern Reader-Processor-Writer.
 * 
 * Architecture:
 * - READER: Récupère les documents avec status FETCHED
 * - PROCESSOR: Télécharge les PDFs via PdfDownloadService
 * - WRITER: Sauvegarde les entités avec status DOWNLOADED et pdfPath
 */
@Slf4j
public class DownloadServiceImpl implements DownloadService {
    
    private static DownloadServiceImpl instance;
    
    private final AppConfig config;
    private final LawDocumentService lawDocumentService;
    private final FileStorageService fileStorageService;
    private final PdfDownloadService pdfDownloadService;
    
    private final List<LawDocumentEntity> downloadResults;
    private int successCount;
    private int failedCount;
    
    private DownloadServiceImpl() {
        this.config = AppConfig.get();
        this.lawDocumentService = new LawDocumentService();
        this.fileStorageService = new FileStorageService();
        this.pdfDownloadService = new PdfDownloadService();
        this.downloadResults = new ArrayList<>();
    }
    
    public static synchronized DownloadServiceImpl getInstance() {
        if (instance == null) {
            instance = new DownloadServiceImpl();
        }
        return instance;
    }
    
    @Override
    public void runType(String type) {
        runType(type, Integer.MAX_VALUE);
    }
    
    @Override
    public void runType(String type, int maxDocuments) {
        log.info("⬇️  DownloadService: type={}, maxDocuments={}", type, maxDocuments);
        
        // Réinitialiser compteurs
        this.successCount = 0;
        this.failedCount = 0;
        this.downloadResults.clear();
        
        // ========== READER: Récupérer documents à télécharger ==========
        List<LawDocumentEntity> documents = readDocumentsToDownload(type, maxDocuments);
        
        if (documents.isEmpty()) {
            log.warn("⚠️ Aucun document FETCHED à télécharger");
            return;
        }
        
        // ========== PROCESSOR: Télécharger les PDFs ==========
        log.info("📥 Processing {} documents...", documents.size());
        for (LawDocumentEntity doc : documents) {
            processDocument(doc);
        }
        
        // ========== WRITER: Sauvegarder les résultats ==========
        writeDownloadResults(this.downloadResults);
        
        // ========== STATISTIQUES ==========
        log.info("✅ DownloadService terminé: {} succès, {} échecs", successCount, failedCount);
    }
    
    // ========== READER ==========
    
    /**
     * READER: Récupère les documents à télécharger.
     * - Charge documents avec status FETCHED et CORRUPTED
     * - Limite à maxDocuments
     * - Filtre ceux déjà téléchargés (idempotence)
     * 
     * @return Liste des documents à traiter
     */
    private List<LawDocumentEntity> readDocumentsToDownload(String type, int maxDocuments) {
        log.info("📖 READER: Récupération documents FETCHED et CORRUPTED...");
        
        // Récupérer documents FETCHED
        List<LawDocumentEntity> documents = new ArrayList<>(
            lawDocumentService.findByTypeAndStatus(type, ProcessingStatus.FETCHED)
        );
        
        // Ajouter documents CORRUPTED (à retélécharger)
        List<LawDocumentEntity> corruptedDocs = lawDocumentService.findByTypeAndStatus(type, ProcessingStatus.CORRUPTED);
        documents.addAll(corruptedDocs);
        
        log.info("📖 READER: {} documents FETCHED, {} documents CORRUPTED", 
                 documents.size() - corruptedDocs.size(), corruptedDocs.size());
        
        // Limiter au maxDocuments
        if (documents.size() > maxDocuments) {
            documents = documents.subList(0, maxDocuments);
        }
        
        log.info("📖 READER: {} documents à télécharger", documents.size());
        return documents;
    }
    
    // ========== PROCESSOR ==========
    
    /**
     * PROCESSOR: Télécharge un document.
     * - Vérifie si déjà téléchargé (fichier existe)
     * - Gère les fichiers corrompus (suppression + retéléchargement)
     * - Appelle PdfDownloadService pour télécharger
     * - Crée entité avec status DOWNLOADED/FAILED
     * - Ajoute à la liste des résultats
     */
    private void processDocument(LawDocumentEntity doc) {
        String documentId = doc.getDocumentId();
        log.debug("⚙️ PROCESSOR: {}", documentId);
        
        try {
            Path pdfPath = fileStorageService.pdfPath(doc.getType(), documentId);
            
            // Si document CORRUPTED, supprimer le fichier avant de retélécharger
            if (doc.getStatus() == ProcessingStatus.CORRUPTED) {
                if (Files.exists(pdfPath)) {
                    log.warn("🗑️ Suppression fichier corrompu: {}", documentId);
                    Files.delete(pdfPath);
                }
                log.info("🔄 Retéléchargement fichier corrompu: {}", documentId);
            }
            // Vérifier si déjà téléchargé (idempotence pour status FETCHED)
            else if (Files.exists(pdfPath)) {
                log.debug("⏭️ Déjà téléchargé: {}", documentId);
                doc.setStatus(ProcessingStatus.DOWNLOADED);
                doc.setPdfPath(pdfPath.toString());
                this.downloadResults.add(doc);
                successCount++;
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
            doc.setPdfPath(pdfPath.toString());
            doc.setErrorMessage(null);  // Effacer message d'erreur précédent si corrompu
            this.downloadResults.add(doc);
            
            log.info("✅ Téléchargé: {} (hash: {})", documentId, hash.substring(0, 8));
            successCount++;
            
        } catch (Exception e) {
            log.error("❌ Erreur download {}: {}", documentId, e.getMessage());
            doc.setStatus(ProcessingStatus.FAILED);
            doc.setErrorMessage(e.getMessage());
            this.downloadResults.add(doc);
            failedCount++;
        }
    }
    
    // ========== WRITER ==========
    
    /**
     * WRITER: Sauvegarde tous les résultats en batch.
     * Utilise saveAll() pour optimiser les performances.
     */
    private void writeDownloadResults(List<LawDocumentEntity> results) {
        if (results.isEmpty()) {
            log.info("💾 WRITER: Aucun résultat à sauvegarder");
            return;
        }
        
        log.info("💾 WRITER: Sauvegarde de {} résultats...", results.size());
        lawDocumentService.saveAll(results);
        log.info("💾 WRITER: ✅ Sauvegarde terminée");
    }
    
    // ========== MÉTHODE INDIVIDUELLE ==========
    
    /**
     * Télécharge un document spécifique par son ID.
     * Pour traiter plusieurs documents, utiliser runType(type, maxDocuments).
     */
    @Override
    public void runDocument(String documentId) {
        log.info("⬇️  download: documentId={}", documentId);
        
        try {
            // Chercher le document
            Optional<LawDocumentEntity> docOpt = lawDocumentService.findByDocumentId(documentId);
            if (docOpt.isEmpty()) {
                log.warn("⚠️ Document non trouvé: {}", documentId);
                return;
            }
            
            LawDocumentEntity doc = docOpt.get();
            
            // Vérifier statut
            if (doc.getStatus() != ProcessingStatus.FETCHED) {
                log.warn("⚠️ Statut incorrect: {} (attendu: FETCHED)", doc.getStatus());
                return;
            }
            
            // Traiter
            processDocument(doc);
            
            // Sauvegarder
            lawDocumentService.save(doc);
            
        } catch (Exception e) {
            log.error("❌ Erreur: {}", e.getMessage());
        }
    }
}
