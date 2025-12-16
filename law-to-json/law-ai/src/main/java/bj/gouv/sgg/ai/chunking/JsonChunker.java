package bj.gouv.sgg.ai.chunking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Découpage de JSON par articles/éléments.
 * 
 * <p><b>Stratégie</b> :
 * <ul>
 *   <li>Préserve structure JSON valide dans chaque chunk</li>
 *   <li>Découpage prioritaire sur array "articles"</li>
 *   <li>Métadonnées copiées dans chaque chunk</li>
 *   <li>Recombine en fusionnant arrays</li>
 * </ul>
 */
@Slf4j
public class JsonChunker implements ChunkingService<JsonObject> {
    
    private static final String ARTICLES_KEY = "articles";
    
    @Override
    public List<JsonObject> chunk(JsonObject content, int maxChunkSize, int overlap) {
        if (!needsChunking(content, maxChunkSize)) {
            return List.of(content);
        }
        
        log.debug("📦 Chunking JSON: {} chars → max {} chars/chunk", 
                content.toString().length(), maxChunkSize);
        
        List<JsonObject> chunks = new ArrayList<>();
        
        // Extrait métadonnées (conservées dans chaque chunk)
        JsonObject metadata = new JsonObject();
        content.entrySet().stream()
                .filter(e -> !e.getKey().equals(ARTICLES_KEY))
                .forEach(e -> metadata.add(e.getKey(), e.getValue()));
        
        // Extrait articles
        if (!content.has(ARTICLES_KEY) || !content.get(ARTICLES_KEY).isJsonArray()) {
            log.warn("⚠️ JSON sans array 'articles', chunking impossible");
            return List.of(content);
        }
        
        JsonArray allArticles = content.getAsJsonArray(ARTICLES_KEY);
        
        // Découpage par taille estimée
        JsonObject currentChunk = createChunkWithMetadata(metadata);
        JsonArray currentArticles = new JsonArray();
        int currentSize = metadata.toString().length();
        
        for (JsonElement article : allArticles) {
            int articleSize = article.toString().length();
            
            if (currentSize + articleSize > maxChunkSize && currentArticles.size() > 0) {
                // Flush chunk actuel
                currentChunk.add(ARTICLES_KEY, currentArticles);
                chunks.add(currentChunk);
                
                // Nouveau chunk
                currentChunk = createChunkWithMetadata(metadata);
                currentArticles = new JsonArray();
                currentSize = metadata.toString().length();
            }
            
            currentArticles.add(article);
            currentSize += articleSize;
        }
        
        // Flush dernier chunk
        if (currentArticles.size() > 0) {
            currentChunk.add(ARTICLES_KEY, currentArticles);
            chunks.add(currentChunk);
        }
        
        log.info("✅ JSON chunked: {} articles → {} chunks", allArticles.size(), chunks.size());
        return chunks;
    }
    
    @Override
    public JsonObject combine(List<JsonObject> processedChunks) {
        if (processedChunks.isEmpty()) {
            return new JsonObject();
        }
        
        if (processedChunks.size() == 1) {
            return processedChunks.get(0);
        }
        
        // Prend métadonnées du premier chunk
        JsonObject combined = new JsonObject();
        JsonObject firstChunk = processedChunks.get(0);
        
        firstChunk.entrySet().stream()
                .filter(e -> !e.getKey().equals(ARTICLES_KEY))
                .forEach(e -> combined.add(e.getKey(), e.getValue()));
        
        // Fusionne tous les articles
        JsonArray allArticles = new JsonArray();
        for (JsonObject chunk : processedChunks) {
            if (chunk.has(ARTICLES_KEY) && chunk.get(ARTICLES_KEY).isJsonArray()) {
                JsonArray chunkArticles = chunk.getAsJsonArray(ARTICLES_KEY);
                chunkArticles.forEach(allArticles::add);
            }
        }
        
        combined.add(ARTICLES_KEY, allArticles);
        
        log.debug("🔗 Combined {} chunks → {} articles", 
                processedChunks.size(), allArticles.size());
        return combined;
    }
    
    @Override
    public boolean needsChunking(JsonObject content, int maxChunkSize) {
        return content != null && content.toString().length() > maxChunkSize;
    }
    
    @Override
    public String getStrategyName() {
        return "JSON_CHUNKER";
    }
    
    // ==================== Méthodes privées ====================
    
    private JsonObject createChunkWithMetadata(JsonObject metadata) {
        JsonObject chunk = new JsonObject();
        metadata.entrySet().forEach(e -> chunk.add(e.getKey(), e.getValue()));
        return chunk;
    }
}
