package bj.gouv.sgg.ai.chunking;

import java.util.List;

/**
 * Service de découpage de contenu en chunks pour traitement IA.
 * 
 * <p><b>Responsabilités</b> :
 * <ul>
 *   <li>Découper contenu volumineux en morceaux gérables</li>
 *   <li>Respecter limites de tokens des modèles IA</li>
 *   <li>Recombiner résultats après traitement</li>
 *   <li>Préserver contexte via overlap entre chunks</li>
 * </ul>
 * 
 * @param <T> Type de contenu à découper
 */
public interface ChunkingService<T> {
    
    /**
     * Découpe le contenu en chunks.
     * 
     * @param content Contenu à découper
     * @param maxChunkSize Taille maximale par chunk (en caractères)
     * @param overlap Nombre de caractères à chevaucher entre chunks
     * @return Liste de chunks
     */
    List<T> chunk(T content, int maxChunkSize, int overlap);
    
    /**
     * Recombine les résultats de chunks traités.
     * 
     * @param processedChunks Chunks traités par IA
     * @return Résultat combiné
     */
    T combine(List<T> processedChunks);
    
    /**
     * Vérifie si le contenu nécessite un découpage.
     * 
     * @param content Contenu à évaluer
     * @param maxChunkSize Taille maximale
     * @return true si chunking nécessaire
     */
    boolean needsChunking(T content, int maxChunkSize);
    
    /**
     * Nom de la stratégie de chunking.
     * 
     * @return Nom identifiant la stratégie
     */
    String getStrategyName();
}
