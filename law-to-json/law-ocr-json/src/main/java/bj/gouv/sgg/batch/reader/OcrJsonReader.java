package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Reader Spring Batch pour extraction JSON.
 * 
 * <p>Lit les documents avec status OCRED_V2 ou FAILED_JSON_EXTRACTION
 * et skip ceux qui ont déjà un jsonPath (idempotence).
 * 
 * <p>Thread-safety : synchronized + ConcurrentLinkedQueue + double-check locking
 */
@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class OcrJsonReader implements ItemReader<LawDocumentEntity> {

    private final LawDocumentRepository repository;
    private final FileStorageService fileStorageService;

    @Value("#{jobParameters['type']}")
    private String type;

    @Value("#{jobParameters['documentId']}")
    private String documentId;

    @Value("#{jobParameters['maxItems'] ?: 1000}")
    private Integer maxItems;

    private ConcurrentLinkedQueue<LawDocumentEntity> documentQueue;
    private volatile boolean initialized = false;

    /**
     * Initialise la queue des documents à traiter (double-check locking).
     */
    private synchronized void initialize() {
        if (!initialized) {
            log.info("🔍 Initialisation OcrJsonReader: type={}, documentId={}, maxItems={}",
                    type, documentId, maxItems);

            List<LawDocumentEntity> documents;

            if (documentId != null && !documentId.isEmpty() && !"ALL".equals(documentId)) {
                // Mode document unique
                String[] parts = documentId.split("-");
                if (parts.length != 3) {
                    log.error("❌ Format documentId invalide: {}", documentId);
                    documentQueue = new ConcurrentLinkedQueue<>();
                    initialized = true;
                    return;
                }
                String docType = parts[0];
                int year = Integer.parseInt(parts[1]);
                String number = parts[2];

                documents = repository.findByTypeAndYearAndNumber(docType, year, number)
                        .map(List::of)
                        .orElseGet(() -> {
                            log.warn("⚠️ Document non trouvé: {}", documentId);
                            return List.of();
                        });
            } else {
                // Mode type complet
                List<ProcessingStatus> targetStatuses = List.of(
                        ProcessingStatus.OCRED_V2,
                        ProcessingStatus.FAILED_EXTRACTION
                );
                documents = repository.findByTypeAndStatusIn(type, targetStatuses);
                if (maxItems != null && documents.size() > maxItems) {
                    documents = documents.subList(0, maxItems);
                }
            }

            // Filtrer les documents qui ont déjà un jsonPath (idempotence)
            List<LawDocumentEntity> filteredDocs = documents.stream()
                    .filter(doc -> {
                        if (doc.getJsonPath() != null && fileStorageService.jsonExists(doc.getType(), doc.getDocumentId())) {
                            log.debug("⏭️ Skip {} (jsonPath exists: {})", doc.getDocumentId(), doc.getJsonPath());
                            return false;
                        }
                        return true;
                    })
                    .toList();

            documentQueue = new ConcurrentLinkedQueue<>(filteredDocs);
            initialized = true;

            log.info("📚 {} documents trouvés pour JSON extraction (après skip: {})",
                    documents.size(), filteredDocs.size());
        }
    }

    @Override
    public synchronized LawDocumentEntity read() {
        if (!initialized) {
            initialize();
        }

        LawDocumentEntity document = documentQueue.poll();
        if (document != null) {
            log.debug("📖 Reading document: {}", document.getDocumentId());
        }
        return document;
    }
}
