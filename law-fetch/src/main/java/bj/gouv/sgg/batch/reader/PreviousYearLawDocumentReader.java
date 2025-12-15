package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.util.LawDocumentFactory;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.FetchCursor;
import bj.gouv.sgg.repository.FetchCursorRepository;
import bj.gouv.sgg.repository.FetchResultRepository;
import bj.gouv.sgg.service.CursorUpdateService;
import bj.gouv.sgg.service.NotFoundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static bj.gouv.sgg.model.LawDocument.TYPE_DECRET;
import static bj.gouv.sgg.model.LawDocument.TYPE_LOI;

/**
 * Reader pour les années précédentes (1960 à année-1)
 * Utilise le cache BD pour éviter les URLs déjà vérifiées
 * Skip automatiquement les plages NOT_FOUND pour optimiser le scan
 */
@Slf4j
@Component
public class PreviousYearLawDocumentReader extends AbstractLawDocumentReader {

    private final NotFoundService notFoundService;

    public PreviousYearLawDocumentReader(LawProperties properties,
                                         FetchResultRepository fetchResultRepository,
                                         FetchCursorRepository fetchCursorRepository,
                                         CursorUpdateService cursorUpdateService,
                                         LawDocumentFactory documentFactory,
                                         NotFoundService notFoundService) {
        super(properties,
                fetchResultRepository,
                fetchCursorRepository,
                cursorUpdateService,
                documentFactory,
                FetchCursor.CURSOR_TYPE_FETCH_PREVIOUS);
        this.notFoundService = notFoundService;
    }


    @Override
    protected List<FetchCursor> getStartCursors() {
        Integer defaultCurrentYearLaw = Calendar.getInstance().get(Calendar.YEAR) - 1;
        Integer defaultCurrentYearDecret = Calendar.getInstance().get(Calendar.YEAR) - 1;

        int defaultCurrent = 2000;

        Optional<FetchCursor> fetchCursorPrevious = fetchCursorRepository
                .findByCursorTypeAndDocumentType(FetchCursor.CURSOR_TYPE_FETCH_PREVIOUS,
                        TYPE_LOI);
        Optional<FetchCursor> fetchCursorPreviousDecret = fetchCursorRepository
                .findByCursorTypeAndDocumentType(FetchCursor.CURSOR_TYPE_FETCH_PREVIOUS,
                        TYPE_DECRET);

        Integer currentYearLaw = fetchCursorPrevious
                .map(FetchCursor::getCurrentYear)
                .orElse(defaultCurrentYearLaw);
        Integer currentYearDecret = fetchCursorPreviousDecret
                .map(FetchCursor::getCurrentYear)
                .orElse(defaultCurrentYearDecret);

        Integer currentNumberForLaw = fetchCursorPrevious
                .map(FetchCursor::getCurrentNumber)
                .orElse(defaultCurrent);
        Integer currentNumberForDecret = fetchCursorPreviousDecret
                .map(FetchCursor::getCurrentNumber)
                .orElse(defaultCurrent);

        FetchCursor lawCursor = FetchCursor.builder()
                .documentType(TYPE_LOI)
                .currentYear(currentYearLaw)
                .currentNumber(currentNumberForLaw)
                .build();
        FetchCursor decretCursor = FetchCursor.builder()
                .documentType(TYPE_DECRET)
                .currentYear(currentYearDecret)
                .currentNumber(currentNumberForDecret)
                .build();
        return Arrays.asList(lawCursor, decretCursor);
    }

    /**
     * Skip les documents marqués NOT_FOUND pour optimiser le scan
     */
    @Override
    protected boolean shouldSkipDocument(String type, Integer year, Integer number) {
        boolean isNotFound = notFoundService.isInNotFoundRange(type, year, number);
        if (isNotFound) {
            log.trace("⏭️ Skipping {}-{}-{} (marked NOT_FOUND)", type, year, number);
        }
        return isNotFound;
    }

    @Override
    protected int getMinYear() {
        return properties.getEndYear(); // 1960 depuis application.yml
    }
    
    @Override
    protected int getMaxDocumentsPerExecution() {
        int maxItems = properties.getBatch().getMaxItemsToFetchPrevious();
        if (maxItems > 0) {
            return maxItems;
        }
        return Integer.MAX_VALUE; // Pas de limite si non configuré
    }
}
