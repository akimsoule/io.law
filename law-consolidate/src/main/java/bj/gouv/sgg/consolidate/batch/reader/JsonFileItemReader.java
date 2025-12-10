package bj.gouv.sgg.consolidate.batch.reader;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

/**
 * ItemReader Spring Batch pour lire les documents à consolider.
 * 
 * <p><b>Critères de lecture</b> :
 * <ul>
 *   <li>Status : {@code EXTRACTED} (JSON généré par law-ocr-json)</li>
 *   <li>Ordre : Chronologique (année DESC, numéro DESC)</li>
 * </ul>
 * 
 * <p><b>Fonctionnement</b> :
 * <ol>
 *   <li>Charge tous les documents EXTRACTED en mémoire au premier appel</li>
 *   <li>Itère sur la liste document par document</li>
 *   <li>Retourne {@code null} quand tous les documents sont traités</li>
 * </ol>
 * 
 * <p><b>Optimisation</b> : Pour volumes importants, pourrait être remplacé par
 * {@code RepositoryItemReader} avec pagination. Pour l'instant, chargement simple
 * car volumes attendus < 10 000 documents.
 * 
 * @see bj.gouv.sgg.consolidate.batch.processor.ConsolidationProcessor
 * @see bj.gouv.sgg.consolidate.batch.writer.ConsolidationWriter
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JsonFileItemReader implements ItemReader<LawDocument> {
    
    private final LawDocumentRepository lawDocumentRepository;
    
    private Iterator<LawDocument> documentIterator;
    private int totalDocuments = 0;
    
    /**
     * Lit le prochain document à consolider.
     * 
     * @return Document EXTRACTED, ou {@code null} si fin de liste
     */
    @Override
    public LawDocument read() {
        // Initialisation lazy au premier appel
        if (documentIterator == null) {
            initializeReader();
        }
        
        // Retourner prochain document ou null si fin
        if (documentIterator.hasNext()) {
            LawDocument doc = documentIterator.next();
            log.debug("📖 [{}] Document lu pour consolidation", doc.getDocumentId());
            return doc;
        }
        
        log.info("✅ Lecture terminée: {} documents EXTRACTED", totalDocuments);
        return null;
    }
    
    /**
     * Initialise le reader : charge documents EXTRACTED depuis BD.
     */
    private void initializeReader() {
        log.info("🔍 Chargement documents EXTRACTED...");
        
        List<LawDocument> documents = lawDocumentRepository.findByStatus(
            LawDocument.ProcessingStatus.EXTRACTED
        );
        
        totalDocuments = documents.size();
        documentIterator = documents.iterator();
        
        log.info("📊 {} documents EXTRACTED à consolider", totalDocuments);
        
        if (totalDocuments == 0) {
            log.warn("⚠️ Aucun document EXTRACTED trouvé. Vérifiez que law-ocr-json a été exécuté.");
        }
    }
}
