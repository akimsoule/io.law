package bj.gouv.sgg.reader;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

import java.util.Iterator;
import java.util.List;

/**
 * Reader Spring Batch pour lire les documents PDF téléchargés (status=DOWNLOADED).
 * 
 * <p>Ce reader sélectionne les documents prêts pour l'extraction JSON :
 * <ul>
 *   <li>Status = DOWNLOADED (PDF téléchargé et disponible)</li>
 *   <li>URL non null (validation)</li>
 * </ul>
 * 
 * <p><b>Modes de fonctionnement</b> :
 * <ul>
 *   <li><b>Mode normal</b> : Lit tous les documents DOWNLOADED (limité par maxDocuments, défaut: 10)</li>
 *   <li><b>Mode ciblé</b> (--doc=xxx) : Traite un seul document spécifique</li>
 *   <li><b>Mode force</b> (--doc + --force) : Re-traite document spécifique même si EXTRACTED (compare confiance)</li>
 * </ul>
 * 
 * <p><b>Note</b> : Le mode force nécessite --doc (document spécifique). En mode global, seuls les DOWNLOADED sont traités.
 * 
 * <p><b>Idempotence</b> : Si status=EXTRACTED déjà présent, le document sera skip
 * par le processor (check confiance JSON existant)
 * 
 * @see bj.gouv.sgg.processor.PdfToJsonProcessor
 */
@RequiredArgsConstructor
@Slf4j
@org.springframework.batch.core.configuration.annotation.StepScope
public class DownloadedPdfReader implements ItemReader<LawDocument> {

    private final LawDocumentRepository lawDocumentRepository;
    
    private Iterator<LawDocument> documentIterator;
    private boolean initialized = false;
    private String targetDocumentId;
    private boolean forceMode = false;
    private Integer maxDocuments = 10; // Par défaut : 10 documents
    
    @Override
    public synchronized LawDocument read() {
        if (!initialized) {
            initialize();
        }
        
        if (documentIterator != null && documentIterator.hasNext()) {
            LawDocument document = documentIterator.next();
            log.debug("📖 Lecture document: {}", document.getDocumentId());
            return document;
        }
        
        return null; // Fin du reader
    }
    
    /**
     * Configure le reader pour traiter un document spécifique
     * @param documentId ID du document (ex: "loi-2024-15")
     */
    public void setTargetDocumentId(String documentId) {
        this.targetDocumentId = documentId;
        log.info("🎯 Document ciblé défini: {}", documentId);
    }
    
    /**
     * Active le mode force (re-traitement même si déjà EXTRACTED)
     */
    public void setForceMode(boolean force) {
        this.forceMode = force;
        log.info("⚡ Mode force: {}", force);
    }
    
    /**
     * Configure le nombre maximum de documents à traiter
     * @param max Nombre maximum (null = pas de limite)
     */
    public void setMaxDocuments(Integer max) {
        this.maxDocuments = max;
        log.info("📊 Nombre max de documents: {}", max != null ? max : "illimité");
    }
    
    private synchronized void initialize() {
        List<LawDocument> toProcess;
        
        // Mode document ciblé
        if (targetDocumentId != null) {
            toProcess = getTargetDocument();
        } else {
            // Mode normal : tous les documents DOWNLOADED (force ignoré en mode global)
            if (forceMode) {
                log.warn("⚠️ Mode force ignoré : --force nécessite --doc=<documentId>");
            }
            
            List<LawDocument> downloadedDocuments = lawDocumentRepository
                .findByStatus(LawDocument.ProcessingStatus.DOWNLOADED);
            
            // Trier du plus récent au plus ancien: year DESC, number DESC
            toProcess = downloadedDocuments.stream()
                .sorted((a, b) -> {
                    if (b.getYear() != a.getYear()) {
                        return Integer.compare(b.getYear(), a.getYear());
                    }
                    return Integer.compare(b.getNumber(), a.getNumber());
                })
                .limit(maxDocuments != null ? maxDocuments : Long.MAX_VALUE)
                .toList();
        }
        
        if (toProcess.isEmpty()) {
            log.info("📄 Aucun document trouvé pour extraction JSON");
            documentIterator = List.<LawDocument>of().iterator();
        } else {
            log.info("📄 {} document(s) prêt(s) pour extraction JSON{}{}", 
                     toProcess.size(),
                     maxDocuments != null && targetDocumentId == null 
                         ? " (limité à " + maxDocuments + ")" : "",
                     forceMode && targetDocumentId != null ? " [MODE FORCE]" : "");
            documentIterator = toProcess.iterator();
        }
        
        initialized = true;
    }
    
    private List<LawDocument> getTargetDocument() {
        // Parser le documentId (ex: "loi-2024-15")
        String[] parts = targetDocumentId.split("-");
        if (parts.length != 3) {
            log.error("❌ Format documentId invalide: {}. Format attendu: type-year-number", targetDocumentId);
            return List.of();
        }
        
        String type = parts[0];
        int year = Integer.parseInt(parts[1]);
        int number = Integer.parseInt(parts[2]);
        
        // Récupérer le document (quel que soit son statut en mode force)
        return lawDocumentRepository.findByTypeAndYearAndNumber(type, year, number)
            .filter(doc -> forceMode 
                        || doc.getStatus() == LawDocument.ProcessingStatus.DOWNLOADED
                        || doc.getStatus() == LawDocument.ProcessingStatus.EXTRACTED)
            .map(List::of)
            .orElse(List.of());
    }
}