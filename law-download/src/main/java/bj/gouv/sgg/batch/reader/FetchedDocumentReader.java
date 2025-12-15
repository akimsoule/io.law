package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.DownloadResultRepository;
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
public class FetchedDocumentReader implements ItemReader<LawDocument> {
    
    private final LawDocumentRepository lawDocumentRepository;
    private final FileStorageService fileStorageService;
    private final DownloadResultRepository downloadResultRepository;
    private Iterator<LawDocument> iterator;
    private String targetDocumentId;
    private boolean forceMode = false;
    private Integer maxDocuments;
    private boolean initialized = false; // Flag pour savoir si le reader a été initialisé
    private String typeFilter; // null = tous, sinon filtre ex: "loi"
    
    @Override
    public synchronized LawDocument read() {
        if (!initialized) {
            initialize();
            initialized = true;
        }
        
        if (iterator != null && iterator.hasNext()) {
            return iterator.next();
        }
        
        return null;
    }
    
    /**
     * Réinitialise le reader (utilisé entre les exécutions de job)
     */
    public synchronized void reset() {
        this.iterator = null;
        this.initialized = false;
        this.targetDocumentId = null;
        this.forceMode = false;
        this.maxDocuments = null;
        log.debug("Reader reset");
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

    /**
     * Filtre optionnel pour ne lire que le type demandé (ex: "loi").
     */
    public void setTypeFilter(String type) {
        if (type != null && !type.isBlank()) {
            this.typeFilter = type.trim().toLowerCase();
            log.info("Type filter enabled: {}", this.typeFilter);
        } else {
            this.typeFilter = null;
            log.info("Type filter disabled (all types)");
        }
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
            if (typeFilter != null) {
                fetchedDocuments = fetchedDocuments.stream()
                        .filter(d -> typeFilter.equalsIgnoreCase(d.getType()))
                        .toList();
            }
            
            // Ajouter les documents DOWNLOADED mais dont le PDF est absent
            List<LawDocument> downloadedDocuments = lawDocumentRepository
                .findByStatus(LawDocument.ProcessingStatus.DOWNLOADED);
            if (typeFilter != null) {
                downloadedDocuments = downloadedDocuments.stream()
                        .filter(d -> typeFilter.equalsIgnoreCase(d.getType()))
                        .toList();
            }
            
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

            // Retirer les documents fetched et dont les fichiers sont déjà présents (sauf en mode force)
            allToDownload = allToDownload.stream()
                .filter(this::shouldDownload)
                .toList(); 
            
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
            .filter(this::shouldDownload)
            .map(List::of)
            .orElse(List.of());
    }
    
    /**
     * Détermine si un document doit être téléchargé selon la logique :
     * 1. Si déjà dans download_results + PDF présent → skip (sauf force)
     * 2. Si FETCHED → télécharger
     * 3. Si DOWNLOADED mais PDF absent → re-télécharger
     */
    private boolean shouldDownload(LawDocument doc) {
        String docId = doc.getDocumentId();
        boolean pdfExists = fileStorageService.pdfExists(doc.getType(), docId);
        boolean existsInDb = downloadResultRepository.existsByDocumentId(docId);
        
        // Cas principal : déjà téléchargé et persisté (skip sauf force)
        if (existsInDb && pdfExists) {
            if (forceMode) {
                log.info("🔄 [{}] Force mode enabled → will re-download", docId);
                return true;
            }
            log.debug("⏭️ [{}] Already in DB and file exists → skip", docId);
            return false;
        }
        
        // Si en DB mais fichier manquant → re-télécharger
        if (existsInDb) {
            log.warn("⚠️ [{}] In DB but PDF missing → will re-download", docId);
            return true;
        }
        
        // Si FETCHED ou DOWNLOADED (non en DB encore) → télécharger
        if (doc.getStatus() == LawDocument.ProcessingStatus.FETCHED ||
            doc.getStatus() == LawDocument.ProcessingStatus.DOWNLOADED) {
            log.debug("✅ [{}] Status {} → will download", docId, doc.getStatus());
            return true;
        }
        
        // Autres statuts → skip (sauf force)
        if (forceMode) {
            log.info("🔄 [{}] Force mode (status={}) → will download", docId, doc.getStatus());
            return true;
        }
        
        log.debug("⏭️ [{}] Status {} → skip", docId, doc.getStatus());
        return false;
    }
}
