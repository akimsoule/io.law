package bj.gouv.sgg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application Spring Boot pour le téléchargement des PDFs.
 * Utilise Spring Batch pour paralléliser les téléchargements.
 * 
 * Architecture:
 * - Reader: Lit les documents FETCHED depuis la base
 * - Processor: Télécharge le PDF et calcule le hash SHA-256
 * - Writer: Met à jour status=DOWNLOADED et pdfPath
 * 
 * Multi-threading:
 * - Adapté automatiquement aux capacités CPU (CPU-1, plafonné à 8)
 * - Configurable via batch.download.thread-pool-size
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LawDownloadApplication {
    
    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(LawDownloadApplication.class, args)));
    }
}
