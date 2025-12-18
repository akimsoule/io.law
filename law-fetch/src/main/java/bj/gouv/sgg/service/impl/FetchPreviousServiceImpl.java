package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.entity.FetchCursorEntity;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.model.ProcessingStatus;
import bj.gouv.sgg.service.FetchCursorService;
import bj.gouv.sgg.service.FetchPreviousService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.*;

/**
 * Implémentation du service de fetch pour les années précédentes.
 * Étend AbstractFetchService pour réutiliser la logique commune.
 * Utilise un cursor pour reprendre là où le scan s'est arrêté.
 */
@Slf4j
public class FetchPreviousServiceImpl extends AbstractFetchService implements FetchPreviousService {
    
    private static FetchPreviousServiceImpl instance;
    
    private final FetchCursorService cursorService;
    private final List<LawDocumentEntity> lawDocumentEntityResult;
    private int newFoundNumber;
    private int newNotFoundNumber;
    
    private FetchPreviousServiceImpl() {
        super();
        this.cursorService = new FetchCursorService();
        this.lawDocumentEntityResult = new ArrayList<>();
    }
    
    public static synchronized FetchPreviousServiceImpl getInstance() {
        if (instance == null) {
            instance = new FetchPreviousServiceImpl();
        }
        return instance;
    }
    
    @Override
    public void runType(String type) {
        // Par défaut, utilise maxItems du config
        run(type, config.getMaxItemsToFetchPrevious());
    }
    
    @Override
    public void run(String type, int maxItems) {
        // start reader
        int currentYear = LocalDate.now().getYear();
        
        // Charger le cursor existant ou partir de (currentYear-1, 1)
        Optional<FetchCursorEntity> cursorOpt = cursorService.getCursor(type, "fetch-previous");
        
        int startYear;
        int startNumber;
        
        if (cursorOpt.isPresent()) {
            FetchCursorEntity cursor = cursorOpt.get();
            startYear = cursor.getCurrentYear();
            startNumber = cursor.getCurrentNumber();
            log.info("🔄 Reprise depuis cursor: type={}, year={}, number={}", 
                     type, startYear, startNumber);
        } else {
            startYear = currentYear - 1;
            startNumber = 1;
            log.info("🆕 Nouveau scan: type={}, years=1960-{}, maxItems={}", 
                     type, startYear, maxItems);
        }
        
        int totalChecked = 0;
        this.newFoundNumber = 0;
        this.newNotFoundNumber = 0;

        // Préparer les documents déjà fetchés pour idempotence (requête optimisée)
        List<LawDocumentEntity> alreadyFetched = lawDocumentService.findFetchedByTypeAndYearRange(
                type, 1960, startYear
        );
        
        // Créer un Set pour recherche rapide O(1)
        Set<String> alreadyFetchedIds = new HashSet<>();
        for (LawDocumentEntity doc : alreadyFetched) {
            alreadyFetchedIds.add(doc.getDocumentId());
        }
        
        log.info("Documents déjà fetchés: {} (seront ignorés)", alreadyFetchedIds.size());
        
        // Générer et traiter documents des années précédentes
        Set<String> documentIds = new LinkedHashSet<>();
        boolean limitReached = false;
        
        for (int year = startYear; year >= 1960 && !limitReached; year--) {
            int numStart = (year == startYear) ? startNumber : 1;
            
            for (int num = numStart; num <= 2000 && !limitReached; num++) {
                String documentId = String.format("%s-%d-%d", type, year, num);
                String documentIdPadded1 = null;
                String documentIdPadded2 = null;
                
                if (num < 10) {
                    documentIdPadded1 = String.format("%s-%d-0%d", type, year, num);
                    documentIdPadded2 = String.format("%s-%d-00%d", type, year, num);
                }
                
                // Ignorer les documents déjà fetchés (toutes variantes)
                if (alreadyFetchedIds.contains(documentId) ||
                    (documentIdPadded1 != null && alreadyFetchedIds.contains(documentIdPadded1)) ||
                    (documentIdPadded2 != null && alreadyFetchedIds.contains(documentIdPadded2))) {
                    continue;
                }
                
                // Vérifier la limite APRÈS avoir filtré les déjà fetchés
                if (totalChecked >= maxItems) {
                    log.info("⏹️ Limite atteinte: {} nouveaux documents à vérifier", maxItems);
                    // Sauvegarder cursor avant de s'arrêter
                    saveCursor(type, year, num);
                    limitReached = true;
                    break;
                }
                
                // Ajouter le document principal
                documentIds.add(documentId);
                totalChecked++;
                
                // Ajouter les variantes avec padding pour num < 10
                if (num < 10) {
                    documentIds.add(documentIdPadded1);
                    documentIds.add(documentIdPadded2);
                }
                
                // Sauvegarder cursor tous les 100 documents
                if (totalChecked % 100 == 0) {
                    saveCursor(type, year, num);
                }
            }
        }

        // end reader
        

        // start processor
        // Traiter les documents
        log.info("Nombre de documents à vérifier: {}", documentIds.size());
        for (String documentId : documentIds) {
            runDocument(documentId);
        }
        // end processor

        // start writer
        // Sauvegarder tous les résultats
        lawDocumentService.saveAll(this.lawDocumentEntityResult);
        // end writer
        
        // Compter les documents trouvés
        long totalFound = 0;
        for (int year = startYear; year >= 1960; year--) {
            totalFound += lawDocumentService.findByTypeAndYearAndStatus(type, year, ProcessingStatus.FETCHED).size();
        }
        
        log.info("🔔 FetchPrevious terminé: type={}, totalChecked={}, newFound={}, newNotFound={}, totalFound={}",
                type, totalChecked, this.newFoundNumber, this.newNotFoundNumber, totalFound);
    }
    
    /**
     * Sauvegarde la position actuelle du cursor
     */
    private void saveCursor(String type, int year, int number) {
        try {
            cursorService.updateCursor(type, "fetch-previous", year, number);
            log.debug("💾 Cursor sauvegardé: type={}, year={}, number={}", type, year, number);
        } catch (Exception e) {
            log.warn("⚠️ Erreur lors de la sauvegarde du cursor: {}", e.getMessage());
        }
    }
    
    @Override
    public void runDocument(String documentId) {
        log.info("🔍 run: documentId={}", documentId);
        
        // Vérifier si documentId est null
        if (documentId == null || documentId.isEmpty()) {
            log.warn("⚠️ documentId null ou vide, ignoring");
            return;
        }
        
        try {
            // Parse documentId
            String[] parts = documentId.split("-");
            if (parts.length != 3) {
                log.warn("⚠️ Format invalide: {}", documentId);
                return;
            }
            
            String type = parts[0];
            int year = Integer.parseInt(parts[1]);
            String number = parts[2];
            
            // Vérifier si déjà fetched
            Optional<LawDocumentEntity> optionalExistingDoc = lawDocumentService.findByDocumentId(documentId);
            if (optionalExistingDoc.isPresent()) {
                LawDocumentEntity existingDoc = optionalExistingDoc.get();
                if (existingDoc.isFetched()) {
                    log.info("ℹ️ Déjà fetché: {}", documentId);
                    return;
                }
            }
            
            // Vérifier existence via HTTP
            boolean found = httpCheckService.checkDocumentExists(type, year, number);
            
            if (found) {
                // Créer/mettre à jour document
                LawDocumentEntity doc = LawDocumentEntity.builder()
                        .type(type)
                        .year(year)
                        .number(number)
                        .status(ProcessingStatus.FETCHED)
                        .build();
                this.lawDocumentEntityResult.add(doc);
                log.info("✅ Found: {}", documentId);
                this.newFoundNumber++;
            } else {
                // Ajouter les not found à la liste
                LawDocumentEntity doc = LawDocumentEntity.builder()
                        .type(type)
                        .year(year)
                        .number(number)
                        .status(ProcessingStatus.NOT_FOUND)
                        .build();
                this.lawDocumentEntityResult.add(doc);
                log.info("❌ Not Found: {}", documentId);
                this.newNotFoundNumber++;
            }
            
        } catch (NumberFormatException e) {
            log.warn("⚠️ Format numérique invalide dans documentId: {}", documentId);
        } catch (bj.gouv.sgg.exception.FetchHttpException e) {
            log.error("❌ Erreur HTTP fetch {}: {} (status: {})", documentId,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    e.getStatusCode());
        } catch (bj.gouv.sgg.exception.FetchTimeoutException e) {
            log.error("❌ Timeout fetch {}: {}", documentId,
                    e.getMessage() != null ? e.getMessage() : "Timeout after retries");
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("❌ Erreur fetch {} [{}]: {}", documentId, e.getClass().getSimpleName(), errorMsg, e);
        }
    }
}
