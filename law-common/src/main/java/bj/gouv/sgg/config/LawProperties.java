package bj.gouv.sgg.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "law")
public class LawProperties {
    
    private String baseUrl;
    private int maxNumberPerYear;
    private String userAgent;
    private int endYear;
    
    private Directories directories = new Directories();
    private Http http = new Http();
    private Ocr ocr = new Ocr();
    private Batch batch = new Batch();
    private Capacity capacity = new Capacity();
    private Groq groq = new Groq();


    @Data
    public static class Directories {
        private String data = "data";            // Répertoire de base pour les fichiers (PDF, OCR, JSON)
        private String pdfs = "pdfs";            // Sous-répertoire pour les PDFs
        private String ocr = "ocr";              // Sous-répertoire pour les OCR
        private String articles = "articles";    // Sous-répertoire pour les JSON
    }
    
    @Data
    public static class Http {
        private int timeout;
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
        private int maxItemsToFetchPrevious; // Limite d'items par exécution pour fetchPreviousJob
        
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
    
    @Data
    public static class Capacity {
        private int ia = 4;              // Score minimum pour IA (16GB+ RAM, 4+ CPU)
        private int ocr = 2;             // Score minimum pour OCR (4GB+ RAM, 2+ CPU)
        private String ollamaUrl = "http://localhost:11434";
        private String ollamaModelsRequired = "gemma3n:latest";
    }
    
    @Data
    public static class Groq {
        private String apiKey;           // API key Groq (optionnel)
    }
}
