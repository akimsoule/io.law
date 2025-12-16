package bj.gouv.sgg.ai.service;

import bj.gouv.sgg.ai.model.TransformationContext;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Service de chunking pour gérer les limites de contexte des modèles IA.
 * 
 * <p><b>Principe</b> : Découper grandes entrées en morceaux compatibles
 * avec la limite de tokens du modèle, avec chevauchement pour préserver contexte.
 * 
 * <p><b>Stratégies</b> :
 * <ul>
 *   <li><b>Texte</b> : Découpe par caractères avec chevauchement</li>
 *   <li><b>PDF</b> : Découpe par pages (images base64)</li>
 *   <li><b>JSON</b> : Découpe par attributs/sections</li>
 * </ul>
 */
@Slf4j
public class ChunkingService {

    /**
     * Ratio approximatif caractères/tokens (4:1 pour texte français).
     */
    private static final int CHARS_PER_TOKEN = 4;
    
    /**
     * Découpe un texte en chunks avec chevauchement.
     * 
     * <p><b>Exemple</b> : Texte de 10000 chars, chunkSize=2000, overlap=200
     * → 5 chunks : [0-2000], [1800-3800], [3600-5600], [5400-7400], [7200-10000]
     * 
     * @param text Texte à découper
     * @param context Contexte avec configuration chunking
     * @return Liste de chunks avec positions
     */
    public List<TextChunk> chunkText(String text, TransformationContext context) {
        int chunkSize = context.getConfig().getChunkSize();
        int overlap = context.getConfig().getChunkOverlap();
        
        List<TextChunk> chunks = new ArrayList<>();
        int textLength = text.length();
        
        if (textLength <= chunkSize) {
            // Pas besoin de chunking
            chunks.add(new TextChunk(text, 0, textLength, 1, 1));
            return chunks;
        }
        
        int start = 0;
        int chunkIndex = 1;
        int totalChunks = estimateChunks(textLength, chunkSize, overlap);
        
        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);
            String chunkText = text.substring(start, end);
            
            chunks.add(new TextChunk(chunkText, start, end, chunkIndex, totalChunks));
            
            // Avancer avec chevauchement
            start = end - overlap;
            if (start >= textLength) {
                break;
            }
            
            chunkIndex++;
        }
        
        log.debug("📦 Text chunked: {} chars → {} chunks (size={}, overlap={})", 
                 textLength, chunks.size(), chunkSize, overlap);
        
        return chunks;
    }
    
    /**
     * Découpe une liste d'images base64 en batches selon limite du modèle.
     * 
     * @param imagesBase64 Images à découper
     * @param maxImagesPerRequest Limite du modèle
     * @return Liste de batches d'images
     */
    public List<ImageBatch> chunkImages(List<String> imagesBase64, int maxImagesPerRequest) {
        List<ImageBatch> batches = new ArrayList<>();
        
        if (imagesBase64.isEmpty()) {
            return batches;
        }
        
        int totalImages = imagesBase64.size();
        int totalBatches = (int) Math.ceil((double) totalImages / maxImagesPerRequest);
        
        for (int i = 0; i < totalImages; i += maxImagesPerRequest) {
            int end = Math.min(i + maxImagesPerRequest, totalImages);
            List<String> batchImages = imagesBase64.subList(i, end);
            
            int batchIndex = (i / maxImagesPerRequest) + 1;
            batches.add(new ImageBatch(batchImages, i, end - 1, batchIndex, totalBatches));
        }
        
        log.debug("📦 Images chunked: {} images → {} batches (max={})", 
                 totalImages, batches.size(), maxImagesPerRequest);
        
        return batches;
    }
    
    /**
     * Estime le nombre de chunks nécessaires pour un texte.
     * 
     * @param textLength Longueur texte en caractères
     * @param chunkSize Taille d'un chunk
     * @param overlap Chevauchement
     * @return Nombre de chunks estimé
     */
    public int estimateChunks(int textLength, int chunkSize, int overlap) {
        if (textLength <= chunkSize) {
            return 1;
        }
        
        int effectiveChunkSize = chunkSize - overlap;
        return (int) Math.ceil((double) (textLength - overlap) / effectiveChunkSize);
    }
    
    /**
     * Estime le nombre de tokens pour un texte.
     * 
     * @param text Texte à estimer
     * @return Nombre de tokens approximatif
     */
    public int estimateTokens(String text) {
        return text.length() / CHARS_PER_TOKEN;
    }
    
    /**
     * Vérifie si un texte nécessite du chunking.
     * 
     * @param text Texte à vérifier
     * @param context Contexte avec configuration
     * @return true si chunking nécessaire
     */
    public boolean requiresChunking(String text, TransformationContext context) {
        int chunkSize = context.getConfig().getChunkSize();
        return text.length() > chunkSize;
    }
    
    /**
     * Chunk de texte avec métadonnées de position.
     */
    @Value
    public static class TextChunk {
        String text;
        int startPosition;
        int endPosition;
        int chunkIndex;
        int totalChunks;
        
        public boolean isFirst() {
            return chunkIndex == 1;
        }
        
        public boolean isLast() {
            return chunkIndex == totalChunks;
        }
    }
    
    /**
     * Batch d'images avec métadonnées.
     */
    @Value
    public static class ImageBatch {
        List<String> imagesBase64;
        int startPageIndex;
        int endPageIndex;
        int batchIndex;
        int totalBatches;
        
        public boolean isFirst() {
            return batchIndex == 1;
        }
        
        public boolean isLast() {
            return batchIndex == totalBatches;
        }
    }
}
