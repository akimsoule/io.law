package bj.gouv.sgg.config;

import bj.gouv.sgg.repository.ErrorCorrectionRepository;
import bj.gouv.sgg.service.OcrService;
import bj.gouv.sgg.service.impl.OcrServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration séparée pour OcrService.
 * Évite la référence circulaire avec OcrBatchConfiguration.
 */
@Configuration
public class OcrServiceConfiguration {
    
    @Bean
    public OcrService ocrService(AppConfig config,
                                 ErrorCorrectionRepository errorCorrectionRepository) {
        return new OcrServiceImpl(config, errorCorrectionRepository);
    }

}
