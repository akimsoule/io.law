package bj.gouv.sgg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application de test pour law-ocr-cor.
 * 
 * <p>Cette classe permet de démarrer le contexte Spring pour les tests d'intégration.
 */
@SpringBootApplication
public class TestApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
