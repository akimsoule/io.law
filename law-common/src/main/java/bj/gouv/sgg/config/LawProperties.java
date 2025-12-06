package bj.gouv.sgg.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "law")
public class LawProperties {
    
    private String baseUrl;
    private int startYear;
    private int maxNumberPerYear;
    private String userAgent;
    private int endYear;
    
    private Directories directories = new Directories();
    private Http http = new Http();
    private Ocr ocr = new Ocr();
    private Batch batch = new Batch();


    @Data
    public static class Directories {
        private String database = "/tmp/law-db"; // Base de données (pas utilisé actuellement)
        private String data = "data";            // Répertoire de base pour les fichiers (PDF, OCR, JSON)
        private String pdfs = "pdfs";            // Sous-répertoire pour les PDFs
        private String ocr = "ocr";              // Sous-répertoire pour les OCR
        private String articles = "articles";    // Sous-répertoire pour les JSON
    }
    
    @Data
    public static class Http {
        private int timeout;
        private int maxRetries;
    }
    
    @Data
    public static class Ocr {
        private String language;
        private int dpi;
        private double qualityThreshold;
    }
    
    @Data
    public static class Batch {
        private int chunkSize;
        private int maxThreads;
        private int throttleLimit;
        private int maxItemsToFetchPrevious = 100; // Limite d'items par exécution pour fetchPreviousJob
        
        /**
         * Retourne le nombre de threads optimal basé sur les CPU disponibles
         * Si maxThreads n'est pas configuré ou ≤ 0, calcule automatiquement
         */
        public int getEffectiveMaxThreads() {
            if (maxThreads > 0) {
                return maxThreads;
            }
            
            // Calculer selon les CPU disponibles : min(10, availableProcessors * 2)
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            int calculatedThreads = Math.min(10, availableProcessors * 2);
            
            return Math.max(1, calculatedThreads); // Au minimum 1 thread
        }
    }
}
