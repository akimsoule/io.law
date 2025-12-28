package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.entity.FetchCursorEntity;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.service.FetchCursorService;
import bj.gouv.sgg.service.LawDocumentValidator;
import bj.gouv.sgg.service.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ItemReader Spring Batch pour fetch previous (années précédentes).
 * Utilise un cursor pour reprendre là où le scan précédent s'est arrêté.
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class FetchPreviousReader implements ItemReader<String> {


    @Value("#{jobParameters['type']}")
    private String type;

    @Value("#{jobParameters['maxItems'] ?: 1000}")
    private int maxItems;

    @Value("#{jobParameters['documentId']}")
    private String documentId;

    private Queue<String> documentIds;
    private final FetchCursorService cursorService;
    private final LawDocumentValidator lawDocumentValidator;
    private final Utils utils;

    @Override
    public synchronized String read() {
        if (documentIds == null) {
            initialize();
        }
        return documentIds.poll();
    }

    /**
     * Initialise le reader avec le cursor et génère les document IDs.
     * Si documentId est fourni, traite uniquement ce document.
     * Synchronized pour thread-safety en mode multi-thread.
     */
    private synchronized void initialize() {
        // Double-check locking pattern
        if (documentIds != null) {
            return;
        }

        // Si un documentId spécifique est fourni (et pas "ALL"), traiter uniquement
        // celui-ci
        if (documentId != null && !documentId.isEmpty() && !"ALL".equals(documentId)) {
            this.documentIds = new ConcurrentLinkedQueue<>();
            documentIds.add(documentId);
            log.info("📖 FetchPreviousReader initialisé pour document spécifique: {}", documentId);
            return;
        }

        // Récupérer le cursor
        Optional<FetchCursorEntity> optionalCursor = cursorService.getCursor(type, "fetch-previous");

        int startYear;
        int startNumber;

        if (optionalCursor.isPresent()) {
            FetchCursorEntity cursor = optionalCursor.get();
            startYear = cursor.getCurrentYear();
            startNumber = cursor.getCurrentNumber();
            log.info("📖 Reprise depuis cursor: type={}, year={}, number={}", type, startYear, startNumber);
        } else {
            // Pas de cursor, on commence l'année dernière
            startYear = LocalDate.now().getYear() - 1;
            startNumber = 1;
            log.info("📖 Nouveau scan: type={}, year={}, number={}", type, startYear, startNumber);
        }

        // Générer les document IDs à partir du cursor
        Set<String> ids = generateDocumentIds(type, startYear, startNumber, maxItems);

        this.documentIds = new ConcurrentLinkedQueue<>(ids);

        log.info("📖 FetchPreviousReader initialisé: {} documents à vérifier (maxItems={})",
                documentIds.size(), maxItems);
    }

    /**
     * Génère les document IDs à partir d'une position de départ.
     */
    private synchronized Set<String> generateDocumentIds(String type, int startYear, int startNumber, int maxItems) {
        Set<String> ids = new LinkedHashSet<>();
        int currentYear = startYear;
        int currentNumber = startNumber;
        int count = 0;

        while (count < maxItems && currentYear >= 1960) {
            Set<String> newIds = utils.getIds(type, currentYear, currentNumber);
            boolean isNotFetched = newIds.stream().map(id -> LawDocumentEntity.createFromDocumentId(id, type))
                    .anyMatch(lawDocumentEntity -> !lawDocumentValidator.isFetched(lawDocumentEntity));

            if (isNotFetched) {
                ids.addAll(newIds);
            } else {
                log.info("Déjà fetched {}", newIds);
            }

            // Si on atteint 2000, passer à l'année précédente
            if (currentNumber > 2000) {
                currentYear--;
                currentNumber = 1;
                log.info("Le cursor passe à l'année {}", currentYear);
            }

            count++;
            currentNumber++;
        }

        return ids;
    }
}
