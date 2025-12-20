package bj.gouv.sgg.batch.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ItemReader Spring Batch pour générer les IDs de documents à vérifier.
 * Génère les document IDs pour l'année courante (1 à 2000).
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class FetchCurrentReader implements ItemReader<String> {
    
    @Value("#{jobParameters['type']}")
    private String type;
    
    @Value("#{jobParameters['documentId']}")
    private String documentId;
    
    private Queue<String> documentIds;
    
    @Override
    public synchronized String read() {
        if (documentIds == null) {
            initialize();
        }
        return documentIds.poll();
    }
    
    /**
     * Initialise le reader avec les document IDs de l'année courante.
     * Si documentId est fourni (et différent de "ALL"), traite uniquement ce document.
     * Synchronized pour thread-safety en mode multi-thread.
     */
    private synchronized void initialize() {
        // Double-check locking pattern
        if (documentIds != null) {
            return;
        }
        
        // Si un documentId spécifique est fourni (et pas "ALL"), traiter uniquement celui-ci
        if (documentId != null && !documentId.isEmpty() && !"ALL".equals(documentId)) {
            this.documentIds = new ConcurrentLinkedQueue<>();
            documentIds.add(documentId);
            log.info("📖 FetchCurrentReader initialisé pour document spécifique: {}", documentId);
            return;
        }
        
        int currentYear = LocalDate.now().getYear();
        Set<String> ids = new LinkedHashSet<>();
        
        // Scanner systématiquement toute l'année courante (1 à 2000)
        for (int num = 1; num <= 2000; num++) {
            String documentId = String.format("%s-%d-%s", type, currentYear, num);
            ids.add(documentId);
            
            // Ajouter les variantes avec padding pour num < 10
            if (num < 10) {
                ids.add(String.format("%s-%d-0%d", type, currentYear, num));
                ids.add(String.format("%s-%d-00%d", type, currentYear, num));
            }
        }
        
        this.documentIds = new ConcurrentLinkedQueue<>(ids);
        
        log.info("📖 FetchCurrentReader initialisé: {} documents à vérifier pour type={}, year={}", 
                 documentIds.size(), type, currentYear);
    }
}
