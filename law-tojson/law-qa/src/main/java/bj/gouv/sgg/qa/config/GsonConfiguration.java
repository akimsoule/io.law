package bj.gouv.sgg.qa.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Gson pour la sérialisation/désérialisation JSON dans le module QA.
 */
@Configuration
public class GsonConfiguration {
    
    @Bean
    public Gson gson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create();
    }
}
