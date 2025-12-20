package bj.gouv.sgg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application Spring Boot pour l'extraction OCR des PDFs.
 * 
 * Workflow:
 * 1. DownloadJob télécharge les PDFs (status: DOWNLOADED)
 * 2. OcrJob extrait le texte via Tesseract (status: OCRED)
 * 3. OcrJsonJob parse le texte en articles JSON
 * 
 * Architecture Spring Batch:
 * - OcrReader: Lit les documents DOWNLOADED depuis la base
 * - OcrProcessor: Effectue l'OCR avec Tesseract
 * - OcrWriter: Sauvegarde le fichier texte et met à jour le statut
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LawOcrApplication {
    
    public static void main(String[] args) {
        System.exit(SpringApplication.exit(
            SpringApplication.run(LawOcrApplication.class, args)
        ));
    }
}
