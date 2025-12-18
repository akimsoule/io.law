package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.service.FetchCurrentService;
import bj.gouv.sgg.service.LawDocumentValidator;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.*;

/**
 * Implémentation du service de fetch pour l'année courante.
 * Étend AbstractFetchService pour réutiliser la logique commune.
 */
@Slf4j
public class FetchCurrentServiceImpl extends AbstractFetchService implements FetchCurrentService {
    
    private static FetchCurrentServiceImpl instance;

    private final List<LawDocumentEntity> lawDocumentEntityResult;
    private int newFoundNumber;
    
    private FetchCurrentServiceImpl() {
        super();
        this.lawDocumentEntityResult = new ArrayList<>();
    }
    
    public static synchronized FetchCurrentServiceImpl getInstance() {
        if (instance == null) {
            instance = new FetchCurrentServiceImpl();
        }
        return instance;
    }
    
    @Override
    public void runType(String type) {
        run(type);
    }
    
    @Override
    public void run(String type) {
        // Start reader for current year
        int currentYear = LocalDate.now().getYear();
        log.info("🔍 FetchCurrent: type={}, year={}", type, currentYear);
        
        int total = 0;
        
        // Vérifier documents 1 à 2000 de l'année courante
        Set<String> documentIds = new LinkedHashSet<>();
        for (int num = 1; num <= 2000; num++) {
            String documentId = String.format("%s-%d-%s", type, currentYear, num);
            documentIds.add(documentId);
            if (num < 10) {
                String documentIdPadded1 = String.format("%s-%d-0%d", type, currentYear, num);
                String documentIdPadded2 = String.format("%s-%d-00%d", type, currentYear, num);
                documentIds.add(documentIdPadded1);
                documentIds.add(documentIdPadded2);
            }
            total++;
        }

        // Retirer les documents déjà fetchés pour l'idempotence
        LawDocumentValidator validator = LawDocumentValidator.getInstance();
        List<LawDocumentEntity> lawDocumentEntityFetched = lawDocumentService.findByTypeAndYear(type, currentYear)
                .stream()
                .filter(validator::isFetched)
                .toList();

        // logger les documents déjà fetchés
        log.info("Documents déjà fetchés: {}", lawDocumentEntityFetched.size());

        for (LawDocumentEntity existingDoc : lawDocumentEntityFetched) {
            String existingDocumentId = existingDoc.getDocumentId();
            documentIds.remove(existingDocumentId);
        }

        // End reader

        // start processor

        // loguer les ids générés
        log.info("Nombre de documents générés {}", documentIds.size());
        for(String documentId : documentIds) {
            runDocument(documentId);
        }

        // end processor

        long totalFound = lawDocumentService.findByTypeAndYearAndStatus(type, currentYear, ProcessingStatus.FETCHED).size();

        // loger les stats
        log.info("🔔 FetchCurrent terminé: type={}, year={}, totalChecked={}, newFound={}, totalFound={}",
                type, currentYear, total, this.newFoundNumber, totalFound);
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
                if (LawDocumentValidator.getInstance().isFetched(existingDoc)) {
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
                this.newFoundNumber = newFoundNumber + 1;

                // start writer
                lawDocumentService.save(doc);
                // end writer
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
