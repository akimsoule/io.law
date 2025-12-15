package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.model.FetchCursor;
import bj.gouv.sgg.repository.FetchCursorRepository;
import bj.gouv.sgg.service.CursorUpdateService;
import bj.gouv.sgg.util.LawDocumentFactory;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.repository.FetchResultRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static bj.gouv.sgg.model.LawDocument.TYPE_DECRET;
import static bj.gouv.sgg.model.LawDocument.TYPE_LOI;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.Calendar;

/**
 * Reader pour l'année courante.
 * Parcourt systématiquement toutes les combinaisons (1..maxNumberPerYear) pour l'année courante
 * en excluant uniquement les documents déjà TROUVÉS (présents dans fetch_results).
 * Les numéros NOT_FOUND sont donc retestés à chaque exécution pour détecter l'apparition tardive.
 */
@Slf4j
@Component
@Getter
public class CurrentYearLawDocumentReader extends AbstractLawDocumentReader {

    public CurrentYearLawDocumentReader(
            LawProperties properties,
            FetchResultRepository fetchResultRepository,
            FetchCursorRepository fetchCursorRepository,
            CursorUpdateService cursorUpdateService,
            LawDocumentFactory documentFactory) {
        super(
                properties,
                fetchResultRepository,
                fetchCursorRepository,
                cursorUpdateService,
                documentFactory,
                FetchCursor.CURSOR_TYPE_FETCH_CURRENT);
    }

    @Override
    protected List<FetchCursor> getStartCursors() {
        Integer currentYear = Calendar.getInstance().get(Calendar.YEAR);
        Integer currentNumberForLaw = fetchCursorRepository.findByCursorTypeAndDocumentType(
                        cursorType,
                        TYPE_LOI)
                .map(FetchCursor::getCurrentNumber)
                .orElse(2000);
        Integer currentNumberForDecret = fetchCursorRepository.findByCursorTypeAndDocumentType(
                        cursorType,
                        TYPE_DECRET)
                .map(FetchCursor::getCurrentNumber)
                .orElse(2000);
        FetchCursor lawCursor = FetchCursor.builder()
                .documentType(TYPE_LOI)
                .currentYear(currentYear)
                .currentNumber(currentNumberForLaw)
                .build();
        FetchCursor decretCursor = FetchCursor.builder()
                .documentType(TYPE_DECRET)
                .currentYear(currentYear)
                .currentNumber(currentNumberForDecret)
                .build();
        return Arrays.asList(lawCursor, decretCursor);
    }

    @Override
    protected int getMinYear() {
        return Calendar.getInstance().get(Calendar.YEAR); // Année courante uniquement
    }

}
