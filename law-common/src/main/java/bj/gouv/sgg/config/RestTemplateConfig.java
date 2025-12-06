package bj.gouv.sgg.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration pour RestTemplate utilisé par LawFetchService
 */
@Configuration
public class RestTemplateConfig {
    
    private final LawProperties properties;
    
    public RestTemplateConfig(LawProperties properties) {
        this.properties = properties;
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofMillis(properties.getHttp().getTimeout()))
            .setReadTimeout(Duration.ofMillis(properties.getHttp().getTimeout()))
            .build();
    }
}
