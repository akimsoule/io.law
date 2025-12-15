package bj.gouv.sgg.consolidate.batch.reader;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
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
    private final FileStorageService fileStorageService;
    
    private Iterator<LawDocument> documentIterator;
    private int totalDocuments = 0;
    private int skippedNoJson = 0;
    private String typeFilter; // null = tous, sinon filtre ex: "loi"
    
    /**
     * Lit le prochain document à consolider.
     * 
     * <p><b>Filtrage</b> : Ne retourne que les documents qui ont un fichier JSON.
     * Les documents EXTRACTED sans JSON sont skippés (log debug).
     * 
     * @return Document EXTRACTED avec JSON existant, ou {@code null} si fin de liste
     */
    @Override
    public LawDocument read() {
        // Initialisation lazy au premier appel
        if (documentIterator == null) {
            initializeReader();
        }
        
        // Chercher prochain document avec JSON existant
        while (documentIterator.hasNext()) {
            LawDocument doc = documentIterator.next();
            String docId = doc.getDocumentId();
            
            // Vérifier existence du fichier JSON
            Path jsonPath = fileStorageService.jsonPath(doc.getType(), docId);
            if (!fileStorageService.jsonExists(doc.getType(), docId)) {
                log.debug("⏭️ [{}] JSON non trouvé, skip (extraction pas encore faite)", docId);
                skippedNoJson++;
                continue; // Skip ce document
            }
            
            log.debug("📖 [{}] Document lu pour consolidation", docId);
            return doc;
        }
        
        // Fin de lecture : log et reset pour prochaine exécution
        if (totalDocuments > 0) {
            log.info("✅ Lecture terminée: {} documents EXTRACTED, {} skippés (pas de JSON)",
                    totalDocuments - skippedNoJson, skippedNoJson);
        }
        documentIterator = null; // Reset pour prochaine exécution du job
        totalDocuments = 0;
        skippedNoJson = 0;
        return null;
    }
    
    /**
     * Définit un filtre de type (ex: "loi" ou "decret").
     */
    public void setTypeFilter(String type) {
        if (type != null && !type.isBlank()) {
            this.typeFilter = type.trim().toLowerCase();
            log.info("🎯 Type filter enabled (consolidate): {}", this.typeFilter);
        } else {
            this.typeFilter = null;
        }
    }
    
    /**
     * Initialise le reader : charge documents EXTRACTED depuis BD.
     */
    private void initializeReader() {
        log.info("🔍 Chargement documents EXTRACTED...");
        
        List<LawDocument> documents = lawDocumentRepository.findByStatus(
            LawDocument.ProcessingStatus.EXTRACTED
        );
        
        if (typeFilter != null) {
            documents = documents.stream()
                .filter(d -> typeFilter.equalsIgnoreCase(d.getType()))
                .toList();
            log.info("🎯 Filtrage par type='{}' appliqué: {} documents", typeFilter, documents.size());
        }
        
        totalDocuments = documents.size();
        documentIterator = documents.iterator();
        
        log.info("📊 {} documents EXTRACTED à consolider", totalDocuments);
        
        if (totalDocuments == 0) {
            log.warn("⚠️ Aucun document EXTRACTED trouvé. Vérifiez que law-ocr-json a été exécuté.");
        }
    }
}
