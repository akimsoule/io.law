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
            
            // Mode normal : INSERT-ONLY (pas d'UPDATE)
            if (!forceMode && exists) {
                log.debug("Already fetched, skipping: {}", document.getDocumentId());
                skippedCount++;
                continue;
            }
            
            // Mode force : DELETE puis flush AVANT de créer le nouveau record
            if (forceMode && exists) {
                repository.deleteByDocumentId(document.getDocumentId());
                entityManager.flush(); // Force commit immédiat du DELETE
                updatedCount++;
                log.debug("Force mode: deleted existing entry for {}", document.getDocumentId());
            }
            
            // Créer un nouvel enregistrement (INSERT)
            FetchResult result = FetchResult.builder()
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
            
            results.add(result);
            if (!exists) {
                newCount++;
            }
            log.debug("{} fetch: {} - Status: {} - Exists: {}", 
                forceMode && exists ? "Updated" : "New",
                document.getDocumentId(), document.getStatus(), document.isExists());
        }
        
        // Sauvegarde en batch pour performance
        if (!results.isEmpty()) {
            repository.saveAll(results);
        }
        
        // Loguer seulement si quelque chose s'est passé
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
