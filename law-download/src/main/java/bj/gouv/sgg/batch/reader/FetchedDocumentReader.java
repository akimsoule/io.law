package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

/**
 * Reader qui lit les documents avec status=FETCHED
 * (documents détectés par law-fetch qui n'ont pas encore été téléchargés)
 * 
 * Logique intelligente :
 * - Si FETCHED → toujours télécharger
 * - Si DOWNLOADED mais PDF absent → re-télécharger automatiquement
 * - Si DOWNLOADED + PDF présent + force → re-télécharger
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.batch.core.configuration.annotation.StepScope
public class FetchedDocumentReader implements ItemReader<LawDocument> {
    
    private final LawDocumentRepository lawDocumentRepository;
    private final FileStorageService fileStorageService;
    private Iterator<LawDocument> iterator;
    private String targetDocumentId;
    private boolean forceMode = false;
    private Integer maxDocuments;
    
    @Override
    public synchronized LawDocument read() {
        if (iterator == null) {
            initialize();
        }
        
        if (iterator.hasNext()) {
            return iterator.next();
        }
        
        return null;
    }
    
    /**
     * Configure le reader pour télécharger un document spécifique
     * @param documentId ID du document (ex: "loi-2024-15")
     */
    public void setTargetDocumentId(String documentId) {
        this.targetDocumentId = documentId;
        log.info("Target document set: {}", documentId);
    }
    
    /**
     * Active le mode force (re-téléchargement même si déjà DOWNLOADED)
     */
    public void setForceMode(boolean force) {
        this.forceMode = force;
        log.info("Force mode: {}", force);
    }
    
    /**
     * Configure le nombre maximum de documents à télécharger
     * @param max Nombre maximum (null = pas de limite)
     */
    public void setMaxDocuments(Integer max) {
        this.maxDocuments = max;
        log.info("Max documents: {}", max != null ? max : "unlimited");
    }
    
    private synchronized void initialize() {
        List<LawDocument> toDownload;
        
        // Mode document ciblé
        if (targetDocumentId != null) {
            toDownload = getTargetDocument();
        } else {
            // Mode normal : tous les documents FETCHED ou DOWNLOADED sans fichier
            List<LawDocument> fetchedDocuments = lawDocumentRepository
                .findByStatus(LawDocument.ProcessingStatus.FETCHED);
            
            // Ajouter les documents DOWNLOADED mais dont le PDF est absent
            List<LawDocument> downloadedDocuments = lawDocumentRepository
                .findByStatus(LawDocument.ProcessingStatus.DOWNLOADED);
            
            List<LawDocument> missingPdfDocuments = downloadedDocuments.stream()
                .filter(doc -> !fileStorageService.pdfExists(doc.getType(), doc.getDocumentId()))
                .toList();
            
            if (!missingPdfDocuments.isEmpty()) {
                log.warn("⚠️ Found {} DOWNLOADED documents with missing PDF files", 
                         missingPdfDocuments.size());
            }
            
            // Combiner FETCHED + DOWNLOADED sans PDF
            List<LawDocument> allToDownload = new java.util.ArrayList<>(fetchedDocuments);
            allToDownload.addAll(missingPdfDocuments);
            
            // Trier du plus récent au plus ancien: year DESC, number DESC
            toDownload = allToDownload.stream()
                .sorted((a, b) -> {
                    if (b.getYear() != a.getYear()) {
                        return Integer.compare(b.getYear(), a.getYear());
                    }
                    return Integer.compare(b.getNumber(), a.getNumber());
                })
                .limit(maxDocuments != null ? maxDocuments : Long.MAX_VALUE)
                .toList();
        }
        
        log.info("📄 {} document(s) ready to download{}", 
                 toDownload.size(),
                 maxDocuments != null ? " (limited to " + maxDocuments + ")" : "");
        
        iterator = toDownload.iterator();
    }
    
    private List<LawDocument> getTargetDocument() {
        // Parser le documentId (ex: "loi-2024-15")
        String[] parts = targetDocumentId.split("-");
        if (parts.length != 3) {
            log.error("❌ Invalid documentId format: {}. Expected format: type-year-number", targetDocumentId);
            return List.of();
        }
        
        String type = parts[0];
        int year = Integer.parseInt(parts[1]);
        int number = Integer.parseInt(parts[2]);
        
        return lawDocumentRepository.findByTypeAndYearAndNumber(type, year, number)
            .filter(doc -> shouldDownload(doc))
            .map(List::of)
            .orElse(List.of());
    }
    
    /**
     * Détermine si un document doit être téléchargé selon la logique :
     * 1. FETCHED → toujours télécharger
     * 2. DOWNLOADED mais PDF absent → re-télécharger automatiquement
     * 3. DOWNLOADED + PDF présent + force → re-télécharger
     * 4. Autres cas → skip
     */
    private boolean shouldDownload(LawDocument doc) {
        String docId = doc.getDocumentId();
        
        // Cas 1 : FETCHED → toujours télécharger
        if (doc.getStatus() == LawDocument.ProcessingStatus.FETCHED) {
            log.info("✅ [{}] Status FETCHED → will download", docId);
            return true;
        }
        
        // Cas 2 : DOWNLOADED mais PDF absent → re-télécharger automatiquement
        if (doc.getStatus() == LawDocument.ProcessingStatus.DOWNLOADED) {
            boolean pdfExists = fileStorageService.pdfExists(doc.getType(), docId);
            
            if (!pdfExists) {
                log.warn("⚠️ [{}] Status DOWNLOADED but PDF missing → will re-download", docId);
                return true;
            }
            
            // Cas 3 : DOWNLOADED + PDF présent + force → re-télécharger
            if (forceMode) {
                log.info("🔄 [{}] Force mode enabled → will re-download", docId);
                return true;
            }
            
            // PDF présent et pas de force → skip
            log.info("⏭️ [{}] Already downloaded and PDF exists → skip", docId);
            return false;
        }
        
        // Autres statuts (EXTRACTED, CONSOLIDATED, etc.) → skip sauf si force
        if (forceMode) {
            log.info("🔄 [{}] Force mode enabled (status={}) → will download", 
                     docId, doc.getStatus());
            return true;
        }
        
        log.debug("⏭️ [{}] Status {} → skip", docId, doc.getStatus());
        return false;
    }
}
