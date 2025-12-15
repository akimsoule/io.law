package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.util.RateLimitHandler;
import org.springframework.stereotype.Component;

/**
 * Processor pour l'année courante.
 * Vérifie l'existence des documents via HTTP HEAD.
 */
@Component
public class CurrentYearLawDocumentProcessor extends AbstractFetchProcessor {

    public CurrentYearLawDocumentProcessor(
            LawProperties properties,
            RateLimitHandler rateLimitHandler
    ) {
        super(properties, rateLimitHandler);
    }
}
