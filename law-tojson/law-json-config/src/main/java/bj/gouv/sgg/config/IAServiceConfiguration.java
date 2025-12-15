package bj.gouv.sgg.config;

import bj.gouv.sgg.impl.OllamaClient;
import bj.gouv.sgg.service.IAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration du bean IAService - OllamaClient uniquement.
 * 
 * <p><b>Stratégie</b> :
 * <ul>
 *   <li><b>OllamaClient</b> : Extraction IA via Ollama local
 *       <ul>
 *         <li>Condition : {@code law.capacity.ia >= 4} (16GB+ RAM)</li>
 *         <li>Vérifications : Ollama pingable + modèle disponible</li>
 *         <li>Avantage : Gratuit, rapide, privé</li>
 *       </ul>
 *   </li>
 * </ul>
 * 
 * <p><b>Note</b> : Ce bean est utilisé par {@link bj.gouv.sgg.processor.PdfToJsonProcessor} pour injection.
 * Fallback vers OCR si Ollama indisponible.
 * 
 * @see bj.gouv.sgg.service.IAService
 * @see bj.gouv.sgg.impl.OllamaClient
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class IAServiceConfiguration {

    private final OllamaClient ollamaClient;
    private final bj.gouv.sgg.config.LawProperties lawProperties;

    /**
     * Bean IAService - Retourne OllamaClient si disponible.
     * 
     * <p><b>Détection capacité</b> : RAM + CPU → Score 0-10
     * <ul>
     *   <li>Score 0-3 : Machine faible → OCR seulement</li>
     *   <li>Score 4+ : Machine moyenne/puissante → IA locale possible</li>
     * </ul>
     * 
     * @return IAService instance (OllamaClient)
     */
    @Bean
    public IAService iaService() {
        // Détecter capacité machine
        long totalMemoryGB = Runtime.getRuntime().maxMemory() / (1024 * 1024 * 1024);
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int capacityScore = calculateCapacityScore(totalMemoryGB, availableProcessors);
        
        log.info("🖥️ Capacité machine détectée : {} GB RAM, {} CPU → Score: {}", 
                 totalMemoryGB, availableProcessors, capacityScore);
        
        // Vérifier si capacité IA suffisante (>=4)
        if (capacityScore >= lawProperties.getCapacity().getIa()) {
            // Vérifier si Ollama est disponible
            try {
                if (ollamaClient.isAvailable()) {
                    log.info("✅ IAService sélectionné : OllamaClient (capacité IA suffisante + Ollama disponible)");
                    return ollamaClient;
                } else {
                    log.warn("⚠️ OllamaClient non disponible (Ollama non pingable ou modèle manquant)");
                }
            } catch (Exception e) {
                log.warn("⚠️ Erreur vérification OllamaClient : {}", e.getMessage());
            }
        } else {
            log.info("⏭️ OllamaClient ignoré (capacité {} < minimum {})", 
                     capacityScore, lawProperties.getCapacity().getIa());
        }
        
        // Si Ollama non disponible, retourner OllamaClient quand même (le processor fera fallback vers OCR)
        log.info("ℹ️ IAService retourne OllamaClient (fallback vers OCR dans le processor si indisponible)");
        return ollamaClient;
    }
    
    /**
     * Calcule un score de capacité machine (0-10) basé sur RAM et CPU.
     * 
     * <p><b>Formule</b> : {@code (RAM_GB / 4) + (CPU / 2)}
     * <ul>
     *   <li>Score 0-1 : Machine très faible (2GB RAM, 1 CPU)</li>
     *   <li>Score 2-3 : Machine faible (4-8GB RAM, 2-4 CPU) - OCR seulement</li>
     *   <li>Score 4-6 : Machine moyenne (16GB RAM, 4-8 CPU) - IA locale possible</li>
     *   <li>Score 7-10 : Machine puissante (32GB+ RAM, 8+ CPU) - IA locale optimale</li>
     * </ul>
     * 
     * @param totalMemoryGB Mémoire totale en GB
     * @param availableProcessors Nombre de CPU disponibles
     * @return Score de capacité (0-10)
     */
    private int calculateCapacityScore(long totalMemoryGB, int availableProcessors) {
        int ramScore = (int) (totalMemoryGB / 4);      // 4GB = 1 point, 16GB = 4 points, 32GB = 8 points
        int cpuScore = availableProcessors / 2;         // 2 CPU = 1 point, 4 CPU = 2 points, 8 CPU = 4 points
        return Math.min(10, ramScore + cpuScore);       // Max 10 points
    }
}
