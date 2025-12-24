package bj.gouv.sgg.batch.config;

import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.repository.ErrorCorrectionRepository;
import bj.gouv.sgg.service.LawDocumentService;
import bj.gouv.sgg.service.correction.impl.CorrectOcrTextImpl;
import bj.gouv.sgg.service.correction.impl.LinguisticCorrectionImpl;
import bj.gouv.sgg.service.extract.OcrExtractionService;
import bj.gouv.sgg.service.extract.impl.OcrExtractionServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration pour les beans d'extraction d'articles.
 * Fournit ArticleExtractorConfig comme bean Spring.
 */
@Configuration
public class OcrConfig {

    @Bean
    public ArticleExtractorConfig articleExtractorConfig(ErrorCorrectionRepository errorCorrectionRepository) {
        return new ArticleExtractorConfig(errorCorrectionRepository);
    }

    @Bean
    public LinguisticCorrectionImpl linguisticCorrectionService() {
        return new LinguisticCorrectionImpl();
    }

    @Bean
    public CorrectOcrTextImpl getCsvCorrector(LawDocumentService lawDocumentService,
                                              ErrorCorrectionRepository errorCorrectionRepository,
                                              ArticleExtractorConfig articleExtractorConfig,
                                              LinguisticCorrectionImpl linguisticCorrectionService) {
        return new CorrectOcrTextImpl(lawDocumentService,
                errorCorrectionRepository,
                articleExtractorConfig,
                linguisticCorrectionService);
    }

    @Bean
    public OcrExtractionService ocrExtractionService(ArticleExtractorConfig config) {
        return new OcrExtractionServiceImpl(config);
    }

}
