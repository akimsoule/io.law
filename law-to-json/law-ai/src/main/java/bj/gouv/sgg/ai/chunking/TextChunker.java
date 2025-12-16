package bj.gouv.sgg.ai.chunking;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Découpage de texte brut par paragraphes/lignes.
 * 
 * <p><b>Stratégie</b> :
 * <ul>
 *   <li>Découpage prioritaire sur sauts de ligne doubles (paragraphes)</li>
 *   <li>Si paragraphe trop long : découpage sur sauts simples</li>
 *   <li>Si ligne trop longue : découpage forcé par caractères</li>
 *   <li>Overlap pour préserver contexte entre chunks</li>
 * </ul>
 */
@Slf4j
public class TextChunker implements ChunkingService<String> {
    
    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    private static final String LINE_SEPARATOR = "\n";
    
    @Override
    public List<String> chunk(String content, int maxChunkSize, int overlap) {
        if (!needsChunking(content, maxChunkSize)) {
            return List.of(content);
        }
        
        log.debug("📦 Chunking text: {} chars → max {} chars/chunk (overlap: {})", 
                content.length(), maxChunkSize, overlap);
        
        List<String> chunks = new ArrayList<>();
        
        // Découpage par paragraphes
        String[] paragraphs = content.split(PARAGRAPH_SEPARATOR);
        
        StringBuilder currentChunk = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            // Si paragraphe seul dépasse limite → découpage récursif
            if (paragraph.length() > maxChunkSize) {
                currentChunk = flushAndChunkLongParagraph(chunks, currentChunk, paragraph, maxChunkSize, overlap);
                continue;
            }
            
            // Ajoute paragraphe (flush si nécessaire)
            currentChunk = addParagraphToChunk(chunks, currentChunk, paragraph, maxChunkSize, overlap);
        }
        
        // Flush dernier chunk
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
        }
        
        log.info("✅ Text chunked: {} chars → {} chunks", content.length(), chunks.size());
        return chunks;
    }
    
    @Override
    public String combine(List<String> processedChunks) {
        if (processedChunks.isEmpty()) {
            return "";
        }
        
        if (processedChunks.size() == 1) {
            return processedChunks.get(0);
        }
        
        // Combine avec simple concatenation (overlap déjà géré)
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < processedChunks.size(); i++) {
            if (i > 0) {
                combined.append(PARAGRAPH_SEPARATOR);
            }
            combined.append(processedChunks.get(i));
        }
        
        log.debug("🔗 Combined {} chunks → {} chars", processedChunks.size(), combined.length());
        return combined.toString();
    }
    
    @Override
    public boolean needsChunking(String content, int maxChunkSize) {
        return content != null && content.length() > maxChunkSize;
    }
    
    @Override
    public String getStrategyName() {
        return "TEXT_CHUNKER";
    }
    
    // ==================== Méthodes privées ====================
    
    private StringBuilder flushAndChunkLongParagraph(List<String> chunks, StringBuilder currentChunk, 
                                                      String paragraph, int maxChunkSize, int overlap) {
        // Flush chunk actuel si non vide
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
        }
        
        // Découpe le gros paragraphe
        chunks.addAll(chunkLongParagraph(paragraph, maxChunkSize, overlap));
        return new StringBuilder();
    }
    
    private StringBuilder addParagraphToChunk(List<String> chunks, StringBuilder currentChunk, 
                                              String paragraph, int maxChunkSize, int overlap) {
        int projectedSize = currentChunk.length() + paragraph.length() + PARAGRAPH_SEPARATOR.length();
        
        if (projectedSize > maxChunkSize && !currentChunk.isEmpty()) {
            // Flush chunk actuel avec overlap
            String chunkText = currentChunk.toString();
            chunks.add(chunkText);
            String previousOverlap = extractOverlap(chunkText, overlap);
            currentChunk = new StringBuilder(previousOverlap);
            if (!currentChunk.isEmpty()) {
                currentChunk.append(PARAGRAPH_SEPARATOR);
            }
        }
        
        // Ajoute paragraphe au chunk actuel
        if (!currentChunk.isEmpty()) {
            currentChunk.append(PARAGRAPH_SEPARATOR);
        }
        currentChunk.append(paragraph);
        return currentChunk;
    }
    
    private StringBuilder flushAndChunkLongLine(List<String> chunks, StringBuilder currentChunk, 
                                                String line, int maxChunkSize, int overlap) {
        // Ligne trop longue → découpage forcé
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
        }
        chunks.addAll(forceChunkBySize(line, maxChunkSize, overlap));
        return new StringBuilder();
    }
    
    private StringBuilder addLineToChunk(List<String> chunks, StringBuilder currentChunk, 
                                         String line, int maxChunkSize, int overlap) {
        if (currentChunk.length() + line.length() + 1 > maxChunkSize) {
            String chunkText = currentChunk.toString();
            chunks.add(chunkText);
            String previousOverlap = extractOverlap(chunkText, overlap);
            currentChunk = new StringBuilder(previousOverlap);
            if (!currentChunk.isEmpty()) {
                currentChunk.append(LINE_SEPARATOR);
            }
        }
        
        if (!currentChunk.isEmpty()) {
            currentChunk.append(LINE_SEPARATOR);
        }
        currentChunk.append(line);
        return currentChunk;
    }
    
    private List<String> chunkLongParagraph(String paragraph, int maxChunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        
        // Essai découpage par lignes
        String[] lines = paragraph.split(LINE_SEPARATOR);
        
        if (lines.length > 1) {
            StringBuilder currentChunk = new StringBuilder();
            
            for (String line : lines) {
                if (line.length() > maxChunkSize) {
                    currentChunk = flushAndChunkLongLine(chunks, currentChunk, line, maxChunkSize, overlap);
                    continue;
                }
                
                currentChunk = addLineToChunk(chunks, currentChunk, line, maxChunkSize, overlap);
            }
            
            if (!currentChunk.isEmpty()) {
                chunks.add(currentChunk.toString());
            }
        } else {
            // Pas de lignes → découpage forcé
            chunks.addAll(forceChunkBySize(paragraph, maxChunkSize, overlap));
        }
        
        return chunks;
    }
    
    private List<String> forceChunkBySize(String text, int maxChunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        
        int position = 0;
        while (position < text.length()) {
            int end = Math.min(position + maxChunkSize, text.length());
            chunks.add(text.substring(position, end));
            position += maxChunkSize - overlap;
        }
        
        return chunks;
    }
    
    private String extractOverlap(String text, int overlapSize) {
        if (overlapSize <= 0 || text.length() <= overlapSize) {
            return "";
        }
        return text.substring(text.length() - overlapSize);
    }
}
