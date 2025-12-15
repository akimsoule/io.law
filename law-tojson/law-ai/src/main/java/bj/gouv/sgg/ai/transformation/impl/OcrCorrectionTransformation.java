package bj.gouv.sgg.ai.transformation.impl;

import bj.gouv.sgg.ai.chunking.TextChunker;
import bj.gouv.sgg.ai.model.AIRequest;
import bj.gouv.sgg.ai.model.AIResponse;
import bj.gouv.sgg.ai.model.TransformationContext;
import bj.gouv.sgg.ai.model.TransformationResult;
import bj.gouv.sgg.ai.provider.IAProvider;
import bj.gouv.sgg.ai.service.PromptLoader;
import bj.gouv.sgg.ai.transformation.IATransformation;
import bj.gouv.sgg.exception.IAException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Transformation : OCR brut → OCR corrigé.
 * 
 * <p><b>Objectif</b> : Corriger les erreurs OCR via IA sans inventer de contenu.
 * 
 * <p><b>Exemple</b> :
 * <pre>
 * Entrée  : "Articlc 1e : La préscnte loi..."
 * Sortie  : "Article 1er : La présente loi..."
 * </pre>
 * 
 * <p><b>Anti-hallucination</b> : Prompt strict interdisant ajout de contenu.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OcrCorrectionTransformation implements IATransformation<String, String> {

    private final TextChunker textChunker;
    private final PromptLoader promptLoader;
    
    private static final String PROMPT_NAME = "ocr-correction";

    @Override
    public String getName() {
        return "OCR_CORRECTION";
    }

    @Override
    public Class<String> getInputType() {
        return String.class;
    }

    @Override
    public Class<String> getOutputType() {
        return String.class;
    }

    @Override
    public TransformationResult<String> transform(String ocrText, TransformationContext context) 
            throws IAException {
        long startTime = System.currentTimeMillis();
        
        String docId = context.getDocument().getDocumentId();
        log.info("🔧 [{}] Starting OCR correction via IA ({} chars)", docId, ocrText.length());
        
        // Vérifier si chunking nécessaire
        int maxChunkSize = context.getConfig().getChunkSize();
        int overlap = context.getConfig().getChunkOverlap();
        
        String correctedText;
        int chunksProcessed;
        
        if (textChunker.needsChunking(ocrText, maxChunkSize)) {
            // Traitement par chunks
            List<String> chunks = textChunker.chunk(ocrText, maxChunkSize, overlap);
            log.info("📦 [{}] Processing {} chunks (strategy: {})", 
                    docId, chunks.size(), textChunker.getStrategyName());
            
            List<String> correctedChunks = new ArrayList<>();
            for (String chunk : chunks) {
                String chunkCorrected = correctChunk(chunk, context);
                correctedChunks.add(chunkCorrected);
            }
            
            correctedText = textChunker.combine(correctedChunks);
            chunksProcessed = chunks.size();
        } else {
            // Traitement direct
            correctedText = correctChunk(ocrText, context);
            chunksProcessed = 1;
        }
        
        long durationMs = System.currentTimeMillis() - startTime;
        
        log.info("✅ [{}] OCR correction completed: {} chars → {} chars ({} chunks, {}ms)",
                docId, ocrText.length(), correctedText.length(), chunksProcessed, durationMs);
        
        return TransformationResult.<String>builder()
                .output(correctedText)
                .confidence(0.85) // Confiance modérée (IA peut introduire erreurs)
                .method("IA:" + context.getProvider().getProviderName())
                .durationMs(durationMs)
                .success(true)
                .build();
    }
    
    private String correctChunk(String chunkText, TransformationContext context) throws IAException {
        String prompt = promptLoader.loadPrompt(PROMPT_NAME, chunkText);
        
        AIRequest request = AIRequest.builder()
                .model(selectModel(context))
                .prompt(prompt)
                .temperature(context.getConfig().getTemperature())
                .maxTokens(context.getConfig().getMaxTokens())
                .build();
        
        AIResponse response = context.getProvider().complete(request);
        return response.getGeneratedText().trim();
    }
    
    private String selectModel(TransformationContext context) {
        var modelOpt = context.getProvider()
                .selectBestModel(false, estimateTokensForChunk(context));
        
        return modelOpt.map(IAProvider.ModelInfo::name)
                .orElseThrow(() -> new IAException("No suitable model for OCR correction"));
    }
    
    private int estimateTokensForChunk(TransformationContext context) {
        // Estimation simple: 1 token ≈ 4 caractères
        int chunkSize = context.getConfig().getChunkSize();
        String samplePrompt = promptLoader.loadPrompt(PROMPT_NAME, "X".repeat(chunkSize));
        return samplePrompt.length() / 4;
    }

    @Override
    public boolean canTransform(TransformationContext context) {
        return context.getProvider().isAvailable() &&
               context.getProvider().selectBestModel(false, 2000).isPresent();
    }

    @Override
    public int estimateChunks(String ocrText, TransformationContext context) {
        if (ocrText == null) return 0;
        
        int maxChunkSize = context.getConfig().getChunkSize();
        if (ocrText.length() <= maxChunkSize) {
            return 1;
        }
        
        // Estimation basée sur taille
        int overlap = context.getConfig().getChunkOverlap();
        int effectiveChunkSize = maxChunkSize - overlap;
        return (int) Math.ceil((double) ocrText.length() / effectiveChunkSize);
    }
}
