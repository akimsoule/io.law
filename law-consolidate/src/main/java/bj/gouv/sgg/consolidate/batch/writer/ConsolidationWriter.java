package bj.gouv.sgg.consolidate.batch.writer;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * ItemWriter Spring Batch pour sauvegarder les résultats de consolidation.
 * 
 * <p><b>Responsabilités</b> :
 * <ul>
 *   <li>Mettre à jour status document (CONSOLIDATED ou FAILED)</li>
 *   <li>Persister modifications en base de données</li>
 *   <li>Logger résultats par statut</li>
 * </ul>
 * 
 * <p><b>Note</b> : Les articles, métadonnées et signataires sont déjà persistés
 * par {@link bj.gouv.sgg.consolidate.service.ConsolidationService} dans le processor.
 * Ce writer ne fait que mettre à jour le statut du {@link LawDocument}.
 * 
 * <p><b>Idempotence</b> : La mise à jour de status est idempotente. Relancer
 * le job N fois produit le même résultat.
 * 
 * @see bj.gouv.sgg.consolidate.batch.processor.ConsolidationProcessor
 * @see bj.gouv.sgg.consolidate.service.ConsolidationService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConsolidationWriter implements ItemWriter<LawDocument> {
    
    private final LawDocumentRepository lawDocumentRepository;
    
    @Override
    public void write(Chunk<? extends LawDocument> chunk) {
        int consolidated = 0;
        int failed = 0;
        
        for (LawDocument document : chunk) {
            String docId = document.getDocumentId();
            
            try {
                // Sauvegarder status avec flush immédiat pour garantir commit
                LawDocument saved = lawDocumentRepository.saveAndFlush(document);
                
                // Vérification post-sauvegarde
                if (saved.getStatus() != document.getStatus()) {
                    log.error("❌ [{}] CRITICAL: Status not persisted! Expected {} but got {}", 
                             docId, document.getStatus(), saved.getStatus());
                }
                
                if (saved.getStatus() == LawDocument.ProcessingStatus.CONSOLIDATED) {
                    consolidated++;
                    log.info("✅ [{}] Status mis à jour → CONSOLIDATED", docId);
                } else if (saved.getStatus() == LawDocument.ProcessingStatus.FAILED) {
                    failed++;
                    log.warn("⚠️ [{}] Status mis à jour → FAILED", docId);
                } else {
                    log.debug("📝 [{}] Status: {}", docId, saved.getStatus());
                }
                
            } catch (Exception e) {
                // En cas d'erreur, logger mais ne pas arrêter le job
                log.error("❌ [{}] Erreur sauvegarde status: {}", docId, e.getMessage(), e);
                failed++;
            }
        }
        
        // Résumé du chunk
        if (consolidated > 0 || failed > 0) {
            log.info("📊 Chunk traité: {} consolidés, {} échoués", consolidated, failed);
        }
    }
}
