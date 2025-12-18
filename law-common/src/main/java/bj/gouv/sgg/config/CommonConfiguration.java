package bj.gouv.sgg.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Configuration Spring pour le module law-common.
 * Point d'entrée Spring pour tous les modules.
 */
@Configuration
@ComponentScan(basePackages = "bj.gouv.sgg")
@EntityScan(basePackages = "bj.gouv.sgg.entity")
@EnableJpaRepositories(basePackages = "bj.gouv.sgg.repository")
@Slf4j
public class CommonConfiguration {
    
    public CommonConfiguration() {
        log.info("✅ Law Common Configuration chargée");
    }
}
