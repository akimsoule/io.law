package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.model.DocumentRecord;
import bj.gouv.sgg.model.ProcessingStatus;
import bj.gouv.sgg.service.DocumentService;
import bj.gouv.sgg.service.FetchService;
import bj.gouv.sgg.service.HttpCheckService;
import bj.gouv.sgg.service.NotFoundRecordService;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Classe abstraite de base pour les services de fetch.
 * Fournit la logique commune pour vérifier l'existence d'un document.
 */
@Slf4j
public abstract class AbstractFetchService implements FetchService {
    
    protected final AppConfig config;
    protected final DocumentService documentService;
    protected final NotFoundRecordService notFoundService;
    protected final HttpCheckService httpCheckService;
    
    public AbstractFetchService() {
        this.config = AppConfig.get();
        this.documentService = new DocumentService();
        this.notFoundService = new NotFoundRecordService();
        this.httpCheckService = new HttpCheckService();
    }
    
    @Override
    public void runDocument(String documentId) {
        log.info("🔍 fetch: documentId={}", documentId);
        
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
            int number = Integer.parseInt(parts[2]);
            
            // Vérifier si déjà fetched
            Optional<DocumentRecord> existingDoc = documentService.findByDocumentId(documentId);
            if (existingDoc.isPresent() && existingDoc.get().getStatus() != ProcessingStatus.PENDING) {
                log.debug("⏭️ Déjà fetched: {}", documentId);
                return;
            }
            
            // Vérifier si dans not_found
            if (notFoundService.isNotFound(type, year, number)) {
                log.debug("⏭️ Déjà marqué NOT_FOUND: {}", documentId);
                return;
            }
            
            // Vérifier existence via HTTP
            boolean found = httpCheckService.checkDocumentExists(type, year, number);
            
            if (found) {
                // Créer/mettre à jour document
                DocumentRecord doc = DocumentRecord.builder()
                    .type(type)
                    .year(year)
                    .number(number)
                    .status(ProcessingStatus.FETCHED)
                    .build();
                documentService.save(doc);
                log.info("✅ Found: {}", documentId);
            } else {
                // Marquer NOT_FOUND
                notFoundService.save(documentId, type, year, number);
                log.debug("❌ Not found: {}", documentId);
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
    
    /**
     * Méthode abstraite à implémenter par les services spécialisés.
     * Définit la logique spécifique de fetch pour un type.
     */
    @Override
    public abstract void runType(String type);
}
