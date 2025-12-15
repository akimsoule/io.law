package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.FetchCursor;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchCursorRepository;
import bj.gouv.sgg.repository.FetchResultRepository;
import bj.gouv.sgg.service.CursorUpdateService;
import bj.gouv.sgg.util.LawDocumentFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classe abstraite pour les readers de documents légaux.
 * Factorise la logique commune entre CurrentYearLawDocumentReader et PreviousYearsLawDocumentReader.
 */
@Slf4j
public abstract class AbstractLawDocumentReader implements ItemReader<LawDocument> {
    
    protected final LawProperties properties;
    protected final FetchResultRepository fetchResultRepository;
    protected final FetchCursorRepository fetchCursorRepository;
    protected final CursorUpdateService cursorUpdateService;
    protected final LawDocumentFactory documentFactory;
    protected final String cursorType;

    protected List<LawDocument> documents;
    protected final AtomicInteger currentIndex = new AtomicInteger(0);

    // Configuration
    protected String targetDocumentId;
    protected boolean forceMode = false;
    protected Integer maxDocuments;
    protected String typeFilter; // null = tous, sinon filtre ex: "loi"

    protected AbstractLawDocumentReader(
            LawProperties properties,
            FetchResultRepository fetchResultRepository,
            FetchCursorRepository fetchCursorRepository,
            CursorUpdateService cursorUpdateService,
            LawDocumentFactory documentFactory,
            String cursorType
    ) {
        this.properties = properties;
        this.fetchResultRepository = fetchResultRepository;
        this.fetchCursorRepository = fetchCursorRepository;
        this.cursorUpdateService = cursorUpdateService;
        this.documentFactory = documentFactory;
        this.cursorType = cursorType;
    }

    /**
     * Retourne l'année minimale à scanner.
     * Permet aux sous-classes de définir leur propre limite.
     * @return l'année minimale (ex: 1960 pour PreviousYear, année courante pour CurrentYear)
     */
    protected abstract int getMinYear();

    @Override
    public LawDocument read() {
        // Mode séquentiel: pas besoin de synchronized
        if (targetDocumentId != null) {
            if (documents == null) {
                documents = generateTargetDocument();
                logGenerationSummary();
            }
        } else {
            if (documents == null) {
                documents = generateAllDocuments();
                logGenerationSummary();
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
     *
     * @param documentId ID du document (ex: "loi-2024-17")
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

    /**
     * Configure le nombre maximum de documents à générer
     *
     * @param max Limite de documents (null = utiliser la configuration par défaut)
     */
    public void setMaxDocuments(Integer max) {
        this.maxDocuments = max;
        if (max != null) {
            log.info("Max documents: {}", max);
        }
    }

    /**
     * Filtre optionnel pour ne générer que le type demandé (ex: "loi").
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

    /**
     * Réinitialise le reader pour une nouvelle lecture
     */
    public void reset() {
        currentIndex.set(0);
        documents = null;
    }

    /**
     * Log un résumé de la génération de documents.
     * Peut être surchargée pour personnaliser les logs.
     */
    protected void logGenerationSummary() {
        if (targetDocumentId != null) {
            log.info("Generated {} document(s) for target: {} (force={})", documents.size(), targetDocumentId, forceMode);
        } else {
            log.info("Generated {} documents (force={})", documents.size(), forceMode);
        }
    }

    /**
     * Génère un seul document ciblé depuis son documentId
     * Format attendu: "loi-2024-17" ou "decret-2023-5"
     */
    protected List<LawDocument> generateTargetDocument() {
        List<LawDocument> docs = new ArrayList<>();

        try {
            // Parser le documentId: "loi-2024-17" → ["loi", "2024", "17"]
            String[] parts = targetDocumentId.split("-");
            if (parts.length != 3) {
                log.error("Invalid documentId format: {}. Expected format: loi-2024-17", targetDocumentId);
                return docs;
            }

            String type = parts[0];
            int year = Integer.parseInt(parts[1]);
            int number = Integer.parseInt(parts[2]);

            // Vérifier si déjà existant (sauf en mode force)
            // Note: Les documents RATE_LIMITED ne sont pas comptés comme existants (ils seront repris)
            if (!forceMode && fetchResultRepository.existsByDocumentIdAndNotRateLimited(targetDocumentId)) {
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

    protected abstract List<FetchCursor> getStartCursors();
    
    /**
     * Retourne la limite maximale de documents à générer pour cette exécution.
     * Utilisé par PreviousYear pour limiter via maxItemsToFetchPrevious.
     * @return limite max de documents, ou Integer.MAX_VALUE si aucune limite
     */
    protected int getMaxDocumentsPerExecution() {
        return Integer.MAX_VALUE; // Pas de limite par défaut
    }

    protected List<LawDocument> generateAllDocuments() {
        List<LawDocument> lawDocuments = new ArrayList<>();
        List<FetchCursor> startCursors = getStartCursors();
        if (typeFilter != null) {
            startCursors = startCursors.stream()
                .filter(c -> typeFilter.equalsIgnoreCase(c.getDocumentType()))
                .toList();
        }
        
        // Utiliser maxDocuments du CLI si défini, sinon la config properties
        int maxNumber = (this.maxDocuments != null && this.maxDocuments > 0) 
            ? this.maxDocuments 
            : properties.getMaxNumberPerYear();

        // Validation
        if (maxNumber <= 0) {
            log.warn("⚠️ Invalid maxNumberPerYear: {}, using default 2000", maxNumber);
            maxNumber = 2000;
        }
        
        // Limite globale pour cette exécution (utilisé par fetchPreviousJob)
        int maxDocumentsPerExecution = getMaxDocumentsPerExecution();

        // Parcourir pour loi et decret
        for (FetchCursor cursor : startCursors) {
            // Vérifier si on a atteint la limite globale
            if (lawDocuments.size() >= maxDocumentsPerExecution) {
                log.info("⏸️ Limite atteinte ({} documents), arrêt génération pour type {}", 
                         maxDocumentsPerExecution, cursor.getDocumentType());
                saveCursorPosition(cursor); // Sauvegarder quand même
                break;
            }
            
            // Calculer combien de docs on peut encore générer pour ce cursor
            int remainingSpace = maxDocumentsPerExecution - lawDocuments.size();
            int effectiveMaxNumber = Math.min(maxNumber, remainingSpace);
            
            processDocumentsForCursor(lawDocuments, cursor, effectiveMaxNumber);
            
            // Sauvegarder le cursor APRÈS traitement complet (même si 0 documents générés)
            // Nécessaire pour persister les transitions d'année (ex: 2024→2023)
            saveCursorPosition(cursor);
        }

        return lawDocuments;
    }

    /**
     * Traite les documents pour un cursor donné et retourne le nombre de documents générés
     */
    private int processDocumentsForCursor(List<LawDocument> lawDocuments, FetchCursor cursor, int maxNumber) {
        int count = 0;
        int skipped = 0;
        int lastProcessedNumber = cursor.getCurrentNumber();
        
        // Parcourir du plus récent au plus ancien
        for (int number = cursor.getCurrentNumber(); number > 0 && count < maxNumber; number--, count++) {
            lastProcessedNumber = number;
            
            // Hook : permet aux sous-classes de skipper certains documents (ex: NOT_FOUND ranges)
            if (shouldSkipDocument(cursor.getDocumentType(), cursor.getCurrentYear(), number)) {
                skipped++;
                count--; // Revert increment since we're skipping this document
                continue;
            }
            
            String docId = String.format("%s-%d-%d", cursor.getDocumentType(), cursor.getCurrentYear(), number);
            log.debug("📄 Adding document to fetch: {}", docId);
            lawDocuments.add(documentFactory.create(cursor.getDocumentType(), cursor.getCurrentYear(), number));
        }
        
        // Mettre à jour la position du cursor pour la prochaine exécution
        cursor.setCurrentNumber(lastProcessedNumber - 1);
        
        // Si on atteint 0 ou moins, passer à l'année précédente
        if (cursor.getCurrentNumber() <= 0) {
            int nextYear = cursor.getCurrentYear() - 1;
            
            // Vérifier si on a atteint la limite minimale (1960 pour PreviousYears)
            if (nextYear < getMinYear()) {
                log.info("📅 Reached minimum year ({}), stopping scan for type {}", 
                         getMinYear(), cursor.getDocumentType());
                cursor.setCurrentYear(getMinYear());
                cursor.setCurrentNumber(0); // Marquer comme terminé
            } else {
                cursor.setCurrentYear(nextYear);
                cursor.setCurrentNumber(maxNumber); // Recommencer à maxNumber pour l'année précédente
                log.info("📅 Year {} complete, moving to year {} (starting at number {})", 
                         nextYear + 1, nextYear, maxNumber);
            }
        }
        
        if (skipped > 0) {
            log.info("Generated {} documents for type {} in year {} (skipped {} NOT_FOUND, next start: {}-{})", 
                     count, cursor.getDocumentType(), cursor.getCurrentYear(), 
                     skipped, cursor.getCurrentYear(), cursor.getCurrentNumber());
        } else {
            log.info("Generated {} documents for type {} in year {} (next start: {}-{})", 
                     count, cursor.getDocumentType(), cursor.getCurrentYear(), 
                     cursor.getCurrentYear(), cursor.getCurrentNumber());
        }
        
        return count;
    }

    /**
     * Hook pour permettre aux sous-classes de skipper certains documents.
     * Par défaut, ne skip rien.
     * PreviousYearLawDocumentReader surcharge cette méthode pour skipper les plages NOT_FOUND.
     * 
     * @param type Type du document ("loi" ou "decret")
     * @param year Année du document
     * @param number Numéro du document
     * @return true si le document doit être skip, false sinon
     */
    protected boolean shouldSkipDocument(String type, Integer year, Integer number) {
        return false; // Par défaut : ne skip rien
    }

    /**
     * Sauvegarde la position du cursor en base de données (UPSERT)
     * Thread-safe : délègue au CursorUpdateService avec transaction isolée (REQUIRES_NEW)
     */
    private void saveCursorPosition(FetchCursor cursor) {
        try {
            cursorUpdateService.updateCursor(
                cursorType,
                cursor.getDocumentType(),
                cursor.getCurrentYear(),
                cursor.getCurrentNumber()
            );
        } catch (Exception e) {
            // Log l'erreur mais ne stoppe pas le job pour une erreur de cursor
            log.error("⚠️ Failed to update cursor for {} ({}): {}", 
                     cursor.getDocumentType(), cursorType, e.getMessage());
        }
    }
}
