package bj.gouv.sgg;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Configuration Spring Boot pour les tests d'intégration.
 * Active JPA avec H2 pour les tests du module law-common.
 * 
 * @SpringBootApplication active automatiquement:
 * - @EnableAutoConfiguration (détecte JPA et H2)
 * - @ComponentScan (scan bj.gouv.sgg.*)
 * - @Configuration
 */
@SpringBootApplication
public class TestConfiguration {
}
