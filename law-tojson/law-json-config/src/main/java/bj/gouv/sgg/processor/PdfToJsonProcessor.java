package bj.gouv.sgg.processor;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.service.FileStorageService;
import bj.gouv.sgg.service.LawTransformationService;
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

/**
 * Processeur Spring Batch pour la transformation PDF → JSON avec stratégie de fallback en cascade.
 * 
 * <p><b>Workflow (géré par LawTransformationService)</b> :
 * <pre>
 * 1. OCR + Corrections CSV + Check qualité
 * 2. Si mauvais → AI Correction OCR
 * 3. Extraction Articles + Check qualité JSON
 * 4. Si mauvais → AI Correction JSON  
 * 5. Si toujours mauvais → AI Extraction complète (PDF direct)
 * 6. Si toujours mauvais → FAILED
 * </pre>
 * 
 * @see LawTransformationService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PdfToJsonProcessor implements ItemProcessor<LawDocument, LawDocument> {

    private final FileStorageService fileStorageService;
    private final LawTransformationService transformationService;
    private final Gson gson;
    
    
    /**
     * Transforme un document PDF en JSON via le service d'orchestration.
     * 
     * <p>Délègue toute la logique de fallback au {@link LawTransformationService}.
     * 
     * <p><b>⚠️ RÉSILIENCE</b> : Ne doit JAMAIS throw d'exception. En cas d'erreur,
     * marque le document FAILED et retourne pour continuer le job.
     */
    @Override
    public LawDocument process(LawDocument document) {
        String docId = document.getDocumentId();
        log.info("🔄 [{}] Démarrage transformation PDF → JSON avec fallback cascade", docId);
        
        // 1. Vérifier que le PDF existe
        Path pdfPath = fileStorageService.pdfPath(document.getType(), docId);
        if (!Files.exists(pdfPath)) {
            log.error("❌ [{}] PDF non trouvé: {}", docId, pdfPath);
            document.setStatus(LawDocument.ProcessingStatus.FAILED);
            return document;
        }
        
        // 2. Lire JSON existant pour comparaison
        Path jsonPath = fileStorageService.jsonPath(document.getType(), docId);
        Optional<JsonResult> existingJson = readExistingJson(jsonPath);
        
        if (existingJson.isPresent()) {
            log.info("📄 [{}] JSON existant trouvé (confiance: {}, source: {})", 
                     docId, existingJson.get().getConfidence(), existingJson.get().getSource());
        }
        
        // 3. Transformation via service d'orchestration
        JsonResult result;
        try {
            result = transformationService.transform(document, pdfPath);
            
        } catch (Exception e) {
            log.error("❌ [{}] Échec transformation: {}", docId, e.getMessage(), e);
            document.setStatus(LawDocument.ProcessingStatus.FAILED);
            return document;
        }
        
        // 4. Comparer confiance avec JSON existant
        if (existingJson.isPresent()) {
            double existingConfidence = existingJson.get().getConfidence();
            double newConfidence = result.getConfidence();
            
            if (newConfidence <= existingConfidence) {
                log.info("⏭️ [{}] Conserver JSON existant (confiance {} > nouvelle {})", 
                         docId, existingConfidence, newConfidence);
                document.setStatus(LawDocument.ProcessingStatus.EXTRACTED);
                document.setOcrContent(null); // Ne pas écraser
                return document;
            } else {
                log.info("📝 [{}] Remplacer JSON existant (confiance {} → {})", 
                         docId, existingConfidence, newConfidence);
            }
        }
        
        // 5. Sauvegarder nouveau résultat (Writer le fera)
        log.info("✅ [{}] Transformation réussie via {} (confiance: {})", 
                 docId, result.getSource(), result.getConfidence());
        
        document.setStatus(LawDocument.ProcessingStatus.EXTRACTED);
        document.setOcrContent(result.getJson()); // Réutilisation champ transient pour Writer
        
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
     *     "source": "ollama-gemma3n"
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
