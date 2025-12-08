package bj.gouv.sgg.processor;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.service.FileStorageService;
import bj.gouv.sgg.service.IAService;
import bj.gouv.sgg.service.OcrTransformer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PdfToJsonProcessor implements ItemProcessor<LawDocument, LawDocument> {

    private final FileStorageService fileStorageService;
    private final IAService iaService;
    private final OcrTransformer ocrTransformer;
    private final Gson gson;
    
    @Override
    public LawDocument process(LawDocument document) throws Exception {
        String docId = document.getDocumentId();
        log.info("🔄 [{}] Démarrage transformation PDF → JSON", docId);
        
        // 1. Vérifier que le PDF existe
        Path pdfPath = fileStorageService.pdfPath(document.getType(), docId);
        if (!Files.exists(pdfPath)) {
            log.error("❌ [{}] PDF non trouvé: {}", docId, pdfPath);
            document.setStatus(LawDocument.ProcessingStatus.FAILED);
            return document;
        }
        
        // 2. Lire JSON existant (si présent)
        Path jsonPath = fileStorageService.jsonPath(document.getType(), docId);
        Optional<JsonResult> existingJson = readExistingJson(jsonPath);
        
        if (existingJson.isPresent()) {
            log.info("📄 [{}] JSON existant trouvé (confiance: {}, source: {})", 
                     docId, existingJson.get().getConfidence(), existingJson.get().getSource());
        }
        
        // 3. Appliquer stratégie fallback IA → OCR programmatique
        JsonResult result;
        try {
            // Tenter extraction via IA (Ollama ou Groq si disponibles)
            result = iaService.transform(document, pdfPath);
            
        } catch (Exception iaException) {
            // IA a échoué ou n'est pas disponible → Fallback OCR programmatique
            log.warn("⚠️ [{}] IA non disponible ou échouée: {} → Fallback OCR programmatique", 
                     docId, iaException.getMessage());
            
            try {
                result = ocrTransformer.transform(document, pdfPath);
                log.info("✅ [{}] Fallback OCR programmatique réussi", docId);
                
            } catch (Exception ocrException) {
                log.error("❌ [{}] Échec IA + OCR: IA={}, OCR={}", 
                         docId, iaException.getMessage(), ocrException.getMessage());
                document.setStatus(LawDocument.ProcessingStatus.FAILED);
                return document;
            }
        }
        
        // 4. Comparer confiance avec JSON existant
        if (existingJson.isPresent()) {
            double existingConfidence = existingJson.get().getConfidence();
            double newConfidence = result.getConfidence();
            
            if (newConfidence <= existingConfidence) {
                log.info("⏭️ [{}] Conserver JSON existant (confiance {} > nouvelle {})", 
                         docId, existingConfidence, newConfidence);
                // Garder le JSON existant, ne pas l'écraser
                document.setStatus(LawDocument.ProcessingStatus.EXTRACTED);
                document.setOcrContent(null); // Ne pas écraser le fichier
                return document;
            } else {
                log.info("📝 [{}] Remplacer JSON existant (confiance {} → {})", 
                         docId, existingConfidence, newConfidence);
            }
        }
        
        // 5. Sauvegarder nouveau résultat (Writer le fera)
        log.info("✅ [{}] Extraction réussie via {} (confiance: {})", 
                 docId, result.getSource(), result.getConfidence());
        
        document.setStatus(LawDocument.ProcessingStatus.EXTRACTED);
        
        // Stocker le JSON dans un champ transient pour que le Writer le récupère
        document.setOcrContent(result.getJson()); // Réutilisation du champ transient
        
        return document;
    }
    
    /**
     * Lit le JSON existant et extrait confiance + source.
     * 
     * <p>Format JSON attendu :
     * <pre>{@code
     * {
     *   "_metadata": {
     *     "confidence": 0.95,
     *     "source": "ollama-qwen2.5:7b"
     *   },
     *   ...
     * }
     * }</pre>
     * 
     * @param jsonPath Chemin du fichier JSON
     * @return JsonResult si fichier existe et parsable, sinon Optional.empty()
     */
    private Optional<JsonResult> readExistingJson(Path jsonPath) {
        if (!Files.exists(jsonPath)) {
            return Optional.empty();
        }
        
        try {
            String jsonContent = Files.readString(jsonPath);
            JsonObject jsonObject = gson.fromJson(jsonContent, JsonObject.class);
            
            // Extraire _metadata
            if (!jsonObject.has("_metadata")) {
                log.warn("⚠️ JSON existant sans _metadata: {}", jsonPath);
                return Optional.empty();
            }
            
            JsonObject metadata = jsonObject.getAsJsonObject("_metadata");
            double confidence = metadata.has("confidence") 
                    ? metadata.get("confidence").getAsDouble() 
                    : 0.0;
            String source = metadata.has("source") 
                    ? metadata.get("source").getAsString() 
                    : "unknown";
            
            return Optional.of(new JsonResult(
                jsonContent, 
                confidence, 
                source
            ));
            
        } catch (IOException e) {
            log.warn("⚠️ Impossible de lire JSON existant {}: {}", jsonPath, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("⚠️ Impossible de parser JSON existant {}: {}", jsonPath, e.getMessage());
            return Optional.empty();
        }
    }
}
