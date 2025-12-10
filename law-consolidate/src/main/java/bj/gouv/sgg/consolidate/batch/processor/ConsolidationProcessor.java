package bj.gouv.sgg.consolidate.batch.processor;

import bj.gouv.sgg.consolidate.service.ConsolidationService;
import bj.gouv.sgg.consolidate.exception.ConsolidationException;
import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * ItemProcessor Spring Batch pour consolider les documents.
 * 
 * <p>
 * <b>Responsabilités</b> :
 * <ul>
 * <li>Vérifier idempotence : skip si déjà consolidé (sauf force mode)</li>
 * <li>Valider document : status EXTRACTED, JSON existe</li>
 * <li>Appeler {@link ConsolidationService} pour parser JSON → BD</li>
 * <li>Gérer erreurs : marquer FAILED, logger, continuer job</li>
 * </ul>
 * 
 * <p>
 * <b>Idempotence</b> : Si le document est déjà consolidé, il est skippé
 * par défaut (log debug). Le service peut gérer UPDATE si nécessaire.
 * 
 * <p>
 * <b>Résilience</b> : En cas d'erreur de consolidation, le document est
 * marqué {@code FAILED} et le job continue (pas d'exception throwée).
 * 
 * @see ConsolidationService
 * @see bj.gouv.sgg.consolidate.batch.reader.JsonFileItemReader
 * @see bj.gouv.sgg.consolidate.batch.writer.ConsolidationWriter
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConsolidationProcessor implements ItemProcessor<LawDocument, LawDocument> {

    private final ConsolidationService consolidationService;

    @Override
    public LawDocument process(LawDocument document) {
        String docId = document.getDocumentId();

        try {
            // 1. Idempotence check
            if (document.getStatus() == LawDocument.ProcessingStatus.CONSOLIDATED) {
                log.debug("⏭️ [{}] Déjà consolidé, skip", docId);
                return document;
            }

            // 2. Vérifier status EXTRACTED
            if (document.getStatus() != LawDocument.ProcessingStatus.EXTRACTED) {
                log.warn("⚠️ [{}] Status invalide: {}. Attendu: EXTRACTED",
                        docId, document.getStatus());
                document.setStatus(LawDocument.ProcessingStatus.FAILED);
                return document;
            }

            // 3. Consolider document (parse JSON → BD)
            // La logique de comparaison de confiance est gérée dans le service
            log.info("🔄 [{}] Consolidation en cours...", docId);
            boolean wasConsolidated = consolidationService.consolidateDocument(document);

            // 4. Marquer comme CONSOLIDATED
            document.setStatus(LawDocument.ProcessingStatus.CONSOLIDATED);
            if (wasConsolidated) {
                log.info("✅ [{}] Consolidation réussie", docId);
            } else {
                log.info("⏭️ [{}] Confiance inférieure, données existantes conservées", docId);
            }

            return document;

        } catch (ConsolidationException e) {
            // Erreur métier : JSON invalide, parsing échoué, etc.
            log.error("❌ [{}] Erreur consolidation: {}", docId, e.getMessage());
            document.setStatus(LawDocument.ProcessingStatus.FAILED);
            return document;

        } catch (Exception e) {
            // Erreur inattendue : problème technique, BD, etc.
            log.error("❌ [{}] Erreur inattendue: {}", docId, e.getMessage(), e);
            document.setStatus(LawDocument.ProcessingStatus.FAILED);
            return document;
        }
    }
}
