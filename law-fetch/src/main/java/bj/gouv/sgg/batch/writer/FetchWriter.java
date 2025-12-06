package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.exception.BatchProcessingException;
import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchResultRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Writer spécialisé pour les fetch results
 * INSERT-ONLY : ne fait jamais d'UPDATE (conforme à law.io.v2)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchWriter implements ItemWriter<LawDocument> {
    
    private final FetchResultRepository repository;
    private final EntityManager entityManager;
    private boolean forceMode = false;
    
    /**
     * Active le mode force (écrasement des données existantes)
     */
    public void setForceMode(boolean force) {
        this.forceMode = force;
        log.info("FetchWriter force mode: {}", force);
    }
    
    @Override
    public void write(Chunk<? extends LawDocument> chunk) throws BatchProcessingException {
        List<FetchResult> results = new ArrayList<>();
        int newCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        
        for (LawDocument document : chunk) {
            boolean exists = repository.existsByDocumentId(document.getDocumentId());
            
            if (shouldSkipDocument(exists)) {
                skippedCount++;
                continue;
            }
            
            if (shouldDeleteExisting(exists)) {
                deleteExistingDocument(document.getDocumentId());
                updatedCount++;
            }
            
            FetchResult result = createFetchResult(document);
            results.add(result);
            
            if (!exists) {
                newCount++;
            }
            
            logDocumentProcessed(document, exists);
        }
        
        saveResults(results);
        logSummary(newCount, updatedCount, skippedCount);
    }
    
    /**
     * Vérifie si le document doit être ignoré (mode normal + existe déjà)
     */
    private boolean shouldSkipDocument(boolean exists) {
        if (!forceMode && exists) {
            log.debug("Already fetched, skipping");
            return true;
        }
        return false;
    }
    
    /**
     * Vérifie si l'entrée existante doit être supprimée (mode force + existe)
     */
    private boolean shouldDeleteExisting(boolean exists) {
        return forceMode && exists;
    }
    
    /**
     * Supprime l'entrée existante et flush
     */
    private void deleteExistingDocument(String documentId) {
        repository.deleteByDocumentId(documentId);
        entityManager.flush();
        log.debug("Force mode: deleted existing entry for {}", documentId);
    }
    
    /**
     * Crée un FetchResult depuis un LawDocument
     */
    private FetchResult createFetchResult(LawDocument document) {
        return FetchResult.builder()
            .documentId(document.getDocumentId())
            .documentType(document.getType())
            .year(document.getYear())
            .number(document.getNumber())
            .url(document.getUrl())
            .status(document.getStatus() != null ? document.getStatus().name() : "UNKNOWN")
            .exists(document.isExists())
            .fetchedAt(LocalDateTime.now())
            .errorMessage(null)
            .build();
    }
    
    /**
     * Log le traitement du document
     */
    private void logDocumentProcessed(LawDocument document, boolean exists) {
        log.debug("{} fetch: {} - Status: {} - Exists: {}", 
            forceMode && exists ? "Updated" : "New",
            document.getDocumentId(), document.getStatus(), document.isExists());
    }
    
    /**
     * Sauvegarde les résultats en batch
     */
    private void saveResults(List<FetchResult> results) {
        if (!results.isEmpty()) {
            repository.saveAll(results);
        }
    }
    
    /**
     * Log le résumé de l'opération
     */
    private void logSummary(int newCount, int updatedCount, int skippedCount) {
        if (newCount > 0 || updatedCount > 0 || skippedCount > 0) {
            if (forceMode) {
                log.info("Saved {} fetch results ({} new, {} updated, {} skipped, FORCE mode)", 
                    newCount + updatedCount, newCount, updatedCount, skippedCount);
            } else {
                log.info("Saved {} new fetch results ({} skipped, INSERT-ONLY)", newCount, skippedCount);
            }
        }
    }
}
