package bj.gouv.sgg.batch.config;

import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.repository.ErrorCorrectionRepository;
import bj.gouv.sgg.service.LawDocumentService;
import bj.gouv.sgg.service.correction.impl.CsvCorrectOcr;
import bj.gouv.sgg.service.extract.OcrExtractionService;
import bj.gouv.sgg.service.extract.impl.OcrExtractionServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration pour les beans d'extraction d'articles.
 * Fournit ArticleExtractorConfig comme bean Spring.
 */
@Configuration
public class ArticleExtractorConfiguration {

    @Bean
    public ArticleExtractorConfig articleExtractorConfig() {
        ArticleExtractorConfig config = new ArticleExtractorConfig();
        config.initialize(); // Initialize patterns from patterns.properties
        return config;
    }

    @Bean
    public CsvCorrectOcr getCsvCorrector(LawDocumentService lawDocumentService,
                                         ErrorCorrectionRepository errorCorrectionRepository) {
        return new CsvCorrectOcr(lawDocumentService, errorCorrectionRepository);
    }

    @Bean
    public OcrExtractionService ocrExtractionService(ArticleExtractorConfig config, CsvCorrectOcr csvCorrectOcr) {
        return new OcrExtractionServiceImpl(config, csvCorrectOcr);
    }

}
