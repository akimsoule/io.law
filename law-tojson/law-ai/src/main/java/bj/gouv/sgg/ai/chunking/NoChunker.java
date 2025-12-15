package bj.gouv.sgg.ai.chunking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implémentation passe-through sans découpage.
 * 
 * <p>Utilisé quand le contenu est déjà de taille acceptable
 * ou quand le chunking n'est pas pertinent pour le type.
 * 
 * @param <T> Type de contenu (générique)
 */
@Component
@Slf4j
public class NoChunker<T> implements ChunkingService<T> {
    
    @Override
    public List<T> chunk(T content, int maxChunkSize, int overlap) {
        log.debug("📦 No chunking needed, content within limits");
        return List.of(content);
    }
    
    @Override
    public T combine(List<T> processedChunks) {
        if (processedChunks.isEmpty()) {
            return null;
        }
        
        if (processedChunks.size() > 1) {
            log.warn("⚠️ NoChunker received {} chunks, returning first only", 
                    processedChunks.size());
        }
        
        return processedChunks.get(0);
    }
    
    @Override
    public boolean needsChunking(T content, int maxChunkSize) {
        return false;
    }
    
    @Override
    public String getStrategyName() {
        return "NO_CHUNKER";
    }
}
