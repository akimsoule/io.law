package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

/**
 * Reader qui lit les documents avec status=FETCHED
 * (documents détectés par law-fetch qui n'ont pas encore été téléchargés)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.batch.core.configuration.annotation.StepScope
public class FetchedDocumentReader implements ItemReader<LawDocument> {
    
    private final LawDocumentRepository lawDocumentRepository;
    private Iterator<LawDocument> iterator;
    private String targetDocumentId;
    private boolean forceMode = false;
    
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
    
    private synchronized void initialize() {
        List<LawDocument> toDownload;
        
        // Mode document ciblé
        if (targetDocumentId != null) {
            toDownload = getTargetDocument();
        } else {
            // Mode normal : tous les documents FETCHED
            List<LawDocument> fetchedDocuments = lawDocumentRepository
                .findByStatus(LawDocument.ProcessingStatus.FETCHED);
            
            // Trier du plus récent au plus ancien: year DESC, number DESC
            toDownload = fetchedDocuments.stream()
                .sorted((a, b) -> {
                    if (b.getYear() != a.getYear()) {
                        return Integer.compare(b.getYear(), a.getYear());
                    }
                    return Integer.compare(b.getNumber(), a.getNumber());
                })
                .toList();
        }
        
        log.info("Found {} documents ready to download (status=FETCHED)", toDownload.size());
        
        iterator = toDownload.iterator();
    }
    
    private List<LawDocument> getTargetDocument() {
        // Parser le documentId (ex: "loi-2024-15")
        String[] parts = targetDocumentId.split("-");
        if (parts.length != 3) {
            log.error("Invalid documentId format: {}. Expected format: type-year-number", targetDocumentId);
            return List.of();
        }
        
        String type = parts[0];
        int year = Integer.parseInt(parts[1]);
        int number = Integer.parseInt(parts[2]);
        
        // En mode force, récupérer le document même s'il est DOWNLOADED
        if (forceMode) {
            return lawDocumentRepository.findByTypeAndYearAndNumber(type, year, number)
                .map(List::of)
                .orElse(List.of());
        }
        
        // Mode normal : seulement si FETCHED
        return lawDocumentRepository.findByTypeAndYearAndNumber(type, year, number)
            .filter(doc -> doc.getStatus() == LawDocument.ProcessingStatus.FETCHED)
            .map(List::of)
            .orElse(List.of());
    }
}
