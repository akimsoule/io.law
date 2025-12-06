package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.util.LawDocumentFactory;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchResultRepository;
import lombok.extern.slf4j.Slf4j;

import static bj.gouv.sgg.model.LawDocument.TYPE_DECRET;
import static bj.gouv.sgg.model.LawDocument.TYPE_LOI;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reader pour l'année courante.
 * Parcourt systématiquement toutes les combinaisons (1..maxNumberPerYear) pour l'année courante
 * en excluant uniquement les documents déjà TROUVÉS (présents dans fetch_results).
 * Les numéros NOT_FOUND sont donc retestés à chaque exécution pour détecter l'apparition tardive.
 */
@Slf4j
@Component
public class CurrentYearLawDocumentReader implements ItemReader<LawDocument> {
    
    private final LawProperties properties;
    private final FetchResultRepository fetchResultRepository;
    private final LawDocumentFactory documentFactory;
    private List<LawDocument> documents;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private String targetDocumentId;
    private boolean forceMode = false;
    
    public CurrentYearLawDocumentReader(LawProperties properties,
                                       FetchResultRepository fetchResultRepository,
                                       LawDocumentFactory documentFactory) {
        this.properties = properties;
        this.fetchResultRepository = fetchResultRepository;
        this.documentFactory = documentFactory;
    }
    
    @Override
    public synchronized LawDocument read() {
        if (documents == null) {
            documents = generateDocuments();
            if (targetDocumentId != null) {
                log.info("Generated {} document(s) for target: {} (force={})", documents.size(), targetDocumentId, forceMode);
            } else {
                log.info("Generated {} documents for current year (full scan, no cache, force={})", documents.size(), forceMode);
            }
        }
        
        int index = currentIndex.getAndIncrement();
        if (index < documents.size()) {
            return documents.get(index);
        }
        
        return null; // End of data
    }
    
    /**
     * Configure le reader pour fetch un document spécifique
     * @param documentId ID du document (ex: "loi-2025-17")
     */
    public void setTargetDocumentId(String documentId) {
        this.targetDocumentId = documentId;
        log.info("Target document set: {}", documentId);
    }
    
    /**
     * Active le mode force (écrasement des données existantes)
     */
    public void setForceMode(boolean force) {
        this.forceMode = force;
        log.info("Force mode: {}", force);
    }
    
    private List<LawDocument> generateDocuments() {
        List<LawDocument> docs = new ArrayList<>();

        // Mode document ciblé
        if (targetDocumentId != null) {
            return generateTargetDocument();
        }

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int maxNumber = properties.getMaxNumberPerYear();

        // En mode force, on ignore les documents déjà trouvés
        Set<String> foundDocuments = forceMode ? Collections.emptySet() 
            : new HashSet<>(fetchResultRepository.findAllDocumentIds());
        
        if (forceMode) {
            log.info("Force mode enabled: will re-fetch ALL documents");
        } else {
            log.info("Loaded {} FOUND documents (will skip these)", foundDocuments.size());
        }

        // Générer les lois ET les décrets
        String[] types = {TYPE_LOI, TYPE_DECRET};
        
        for (String type : types) {
            for (int number = 1; number <= maxNumber; number++) {
                // Pour number < 10, tester les deux formats : "1" et "01"
                String docId = String.format("%s-%d-%d", type, currentYear, number);
                String docIdPadded = String.format("%s-%d-%02d", type, currentYear, number);
                
                // Si aucun des deux formats n'existe dans foundDocuments (ou mode force), ajouter le document
                if (!foundDocuments.contains(docId) && !foundDocuments.contains(docIdPadded)) {
                    docs.add(documentFactory.create(type, currentYear, number));
                }
            }
        }

        log.info("Generated {} candidate documents (lois + decrets) for current year {} ({} were already FOUND)", 
            docs.size(), currentYear, foundDocuments.size());

        if (!docs.isEmpty()) {
            LawDocument first = docs.get(0);
            LawDocument last = docs.get(docs.size() - 1);
            log.info("Scan range: {} → {}", first.getUrl(), last.getUrl());
        }

        return docs;
    }
    
    /**
     * Génère un seul document ciblé depuis son documentId
     * Format attendu: "loi-2025-17" ou "decret-2024-5"
     */
    private List<LawDocument> generateTargetDocument() {
        List<LawDocument> docs = new ArrayList<>();
        
        try {
            // Parser le documentId: "loi-2025-17" → ["loi", "2025", "17"]
            String[] parts = targetDocumentId.split("-");
            if (parts.length != 3) {
                log.error("Invalid documentId format: {}. Expected format: loi-2025-17", targetDocumentId);
                return docs;
            }
            
            String type = parts[0];
            int year = Integer.parseInt(parts[1]);
            int number = Integer.parseInt(parts[2]);
            
            // Vérifier si déjà existant (sauf en mode force)
            if (!forceMode && fetchResultRepository.existsByDocumentId(targetDocumentId)) {
                log.warn("Document {} already exists. Use --force to re-fetch", targetDocumentId);
                return docs;
            }
            
            // Créer le document
            LawDocument doc = documentFactory.create(type, year, number);
            docs.add(doc);
            
            log.info("Target document: {} → {} (force={})", targetDocumentId, doc.getUrl(), forceMode);
            
        } catch (NumberFormatException e) {
            log.error("Invalid year or number in documentId: {}", targetDocumentId, e);
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("Invalid documentId format: {}. Expected format: type-year-number", targetDocumentId, e);
        }
        
        return docs;
    }
    
    /**
     * Charge le cursor depuis la BD
     * @return [year, number] - position de départ
     */
    // Cursor logic removed: current-year scan now ignores persisted cursor
    
    public void reset() {
        currentIndex.set(0);
        documents = null;
    }
}
