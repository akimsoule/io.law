package bj.gouv.sgg.batch.config;

import bj.gouv.sgg.config.ArticleExtractorConfig;
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
}
