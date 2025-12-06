package bj.gouv.sgg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application principale du module Law API.
 * Expose l'API REST et orchestre les jobs Spring Batch via endpoints manuels.
 */
@SpringBootApplication
public class LawApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LawApiApplication.class, args);
    }
}
