package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.service.HttpCheckService;
import bj.gouv.sgg.service.LawDocumentService;
import bj.gouv.sgg.service.LawDocumentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * ItemProcessor Spring Batch pour vérifier l'existence des documents de l'année courante.
 * Pour current : ne persiste PAS les NOT_FOUND.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchCurrentProcessor implements ItemProcessor<String, LawDocumentEntity> {
    
    private final HttpCheckService httpCheckService;
    private final LawDocumentService lawDocumentService;
    private final LawDocumentValidator validator;
    
    @Override
    public synchronized LawDocumentEntity process(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            log.warn("⚠️ documentId null ou vide, skipping");
            return null;
        }
        
        try {
            // Parse documentId
            String[] parts = documentId.split("-");
            if (parts.length != 3) {
                log.warn("⚠️ Format invalide: {}", documentId);
                return null;
            }
            
            String type = parts[0];
            int year = Integer.parseInt(parts[1]);
            String number = parts[2];
            
            // Vérifier si déjà fetché (idempotence)
            Optional<LawDocumentEntity> optionalExisting = lawDocumentService.findByDocumentId(documentId);
            if (optionalExisting.isPresent()) {
                LawDocumentEntity existing = optionalExisting.get();
                if (validator.isFetched(existing)) {
                    log.debug("ℹ️ Déjà fetché: {}", documentId);
                    return null;
                }
            }
            
            // Vérifier existence via HTTP
            boolean found = httpCheckService.checkDocumentExists(type, year, number);
            
            if (found) {
                log.info("✅ Found: {}", documentId);
                return LawDocumentEntity.builder()
                        .documentId(documentId)
                        .type(type)
                        .year(year)
                        .number(number)
                        .status(ProcessingStatus.FETCHED)
                        .build();
            }
            
            return null;
            
        } catch (NumberFormatException e) {
            log.warn("⚠️ Format numérique invalide dans documentId: {}", documentId);
            return null;
        } catch (Exception e) {
            log.error("❌ Erreur processing {}: {}", documentId, e.getMessage(), e);
            return null;
        }
    }
}
