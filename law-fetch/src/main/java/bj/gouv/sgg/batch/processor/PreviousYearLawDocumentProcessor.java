package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.util.RateLimitHandler;
import org.springframework.stereotype.Component;

/**
 * Processor pour les années précédentes.
 * Vérifie l'existence des documents via HTTP HEAD.
 * Le traitement des NOT_FOUND est fait dans FetchWriter.
 */
@Component
public class PreviousYearLawDocumentProcessor extends AbstractFetchProcessor {

    public PreviousYearLawDocumentProcessor(
            LawProperties properties,
            RateLimitHandler rateLimitHandler
    ) {
        super(properties, rateLimitHandler);
    }
}
