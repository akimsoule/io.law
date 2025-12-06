package bj.gouv.sgg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Application Spring Boot de test pour le module law-download.
 * Utilisée uniquement pour les tests d'intégration.
 */
@SpringBootApplication(scanBasePackages = {"bj.gouv.sgg"})
@EnableJpaRepositories(basePackages = {"bj.gouv.sgg.repository"})
@EntityScan(basePackages = {"bj.gouv.sgg.model"})
public class TestDownloadApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestDownloadApplication.class, args);
    }
}
