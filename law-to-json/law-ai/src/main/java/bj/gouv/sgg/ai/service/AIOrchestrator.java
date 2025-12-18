package bj.gouv.sgg.ai.service;

import bj.gouv.sgg.ai.model.TransformationContext;
import bj.gouv.sgg.ai.model.TransformationResult;
import bj.gouv.sgg.provider.IAProvider;
import bj.gouv.sgg.provider.IAProviderFactory;
import bj.gouv.sgg.ai.transformation.IATransformation;
import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.entity.LawDocumentEntity;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Service principal d'orchestration des transformations IA.
 * 
 * <p><b>Responsabilités</b> :
 * <ul>
 *   <li>Coordonner les transformations disponibles</li>
 *   <li>Sélectionner le meilleur provider IA</li>
 *   <li>Gérer les fallbacks si transformation échoue</li>
 *   <li>Tracer performances et résultats</li>
 * </ul>
 * 
 * <p><b>Exemple d'usage</b> :
 * <pre>
 * String correctedOcr = orchestrator.correctOcr(document, rawOcrText);
 * JsonObject json = orchestrator.ocrToJson(document, correctedOcr);
 * </pre>
 */
@Slf4j
public class AIOrchestrator {

    private final IAProviderFactory providerFactory;
    private final List<IATransformation<?, ?>> transformations;
    
    public AIOrchestrator(IAProviderFactory providerFactory,
                         List<IATransformation<?, ?>> transformations) {
        this.providerFactory = providerFactory;
        this.transformations = transformations;
    }
    
    /**
     * Corrige un texte OCR brut via IA.
     * 
     * @param document Document source
     * @param rawOcrText Texte OCR non corrigé
     * @return Texte OCR corrigé
     * @throws IAException Si correction échoue
     */
    public String correctOcr(LawDocumentEntity document, String rawOcrText) throws IAException {
        log.info("🔧 [{}] Correcting OCR text ({} chars)", 
                document.getDocumentId(), rawOcrText.length());
        
        IATransformation<String, String> transformation = findTransformation("OCR_CORRECTION");
        TransformationContext context = buildContext(document, false, rawOcrText.length());
        
        if (!transformation.canTransform(context)) {
            throw new IAException("Cannot perform OCR correction: no suitable provider");
        }
        
        TransformationResult<String> result = transformation.transform(rawOcrText, context);
        
        if (!result.isSuccess()) {
            throw new IAException("OCR correction failed: " + result.getErrorMessage());
        }
        
        return result.getOutput();
    }
    
    /**
     * Extrait JSON structuré depuis texte OCR.
     * 
     * @param document Document source
     * @param ocrText Texte OCR (corrigé ou brut)
     * @return JSON structuré
     * @throws IAException Si extraction échoue
     */
    public JsonObject ocrToJson(LawDocumentEntity document, String ocrText) throws IAException {
        log.info("📄 [{}] Extracting JSON from OCR ({} chars)", 
                document.getDocumentId(), ocrText.length());
        
        IATransformation<String, JsonObject> transformation = findTransformation("OCR_TO_JSON");
        TransformationContext context = buildContext(document, false, ocrText.length());
        
        if (!transformation.canTransform(context)) {
            throw new IAException("Cannot extract JSON: no suitable provider");
        }
        
        TransformationResult<JsonObject> result = transformation.transform(ocrText, context);
        
        if (!result.isSuccess()) {
            throw new IAException("JSON extraction failed: " + result.getErrorMessage());
        }
        
        return result.getOutput();
    }
    
    /**
     * Corrige les attributs d'un JSON existant.
     * 
     * @param document Document source
     * @param jsonToCorrect JSON à corriger
     * @return JSON corrigé
     * @throws IAException Si correction échoue
     */
    public JsonObject correctJson(LawDocumentEntity document, JsonObject jsonToCorrect) throws IAException {
        log.info("📝 [{}] Correcting JSON attributes", document.getDocumentId());
        
        IATransformation<JsonObject, JsonObject> transformation = findTransformation("JSON_CORRECTION");
        TransformationContext context = buildContext(document, false, 
                jsonToCorrect.toString().length());
        
        if (!transformation.canTransform(context)) {
            throw new IAException("Cannot correct JSON: no suitable provider");
        }
        
        TransformationResult<JsonObject> result = transformation.transform(jsonToCorrect, context);
        
        if (!result.isSuccess()) {
            throw new IAException("JSON correction failed: " + result.getErrorMessage());
        }
        
        return result.getOutput();
    }
    
    /**
     * Extrait texte OCR directement depuis PDF via IA vision.
     * 
     * @param document Document source
     * @param pdfPath Chemin vers PDF
     * @return Texte extrait
     * @throws IAException Si extraction échoue
     */
    public String pdfToOcr(LawDocumentEntity document, Path pdfPath) throws IAException {
        log.info("📄 [{}] Extracting OCR from PDF: {}", 
                document.getDocumentId(), pdfPath);
        
        IATransformation<Path, String> transformation = findTransformation("PDF_TO_OCR");
        TransformationContext context = buildContext(document, true, 0);
        
        if (!transformation.canTransform(context)) {
            throw new IAException("Cannot extract OCR from PDF: no vision model available");
        }
        
        TransformationResult<String> result = transformation.transform(pdfPath, context);
        
        if (!result.isSuccess()) {
            throw new IAException("PDF to OCR failed: " + result.getErrorMessage());
        }
        
        return result.getOutput();
    }
    
    /**
     * Extrait JSON directement depuis PDF via IA vision (bypass OCR).
     * 
     * @param document Document source
     * @param pdfPath Chemin vers PDF
     * @return JSON structuré
     * @throws IAException Si extraction échoue
     */
    public JsonObject pdfToJson(LawDocumentEntity document, Path pdfPath) throws IAException {
        log.info("📄 [{}] Extracting JSON directly from PDF: {}", 
                document.getDocumentId(), pdfPath);
        
        IATransformation<Path, JsonObject> transformation = findTransformation("PDF_TO_JSON");
        TransformationContext context = buildContext(document, true, 0);
        
        if (!transformation.canTransform(context)) {
            throw new IAException("Cannot extract JSON from PDF: no vision model available");
        }
        
        TransformationResult<JsonObject> result = transformation.transform(pdfPath, context);
        
        if (!result.isSuccess()) {
            throw new IAException("PDF to JSON failed: " + result.getErrorMessage());
        }
        
        return result.getOutput();
    }
    
    /**
     * Vérifie si une transformation est disponible.
     * 
     * @param transformationName Nom de la transformation
     * @return true si disponible, false sinon
     */
    public boolean isTransformationAvailable(String transformationName) {
        try {
            IATransformation<?, ?> transformation = findTransformation(transformationName);
            return transformation != null;
        } catch (IAException e) {
            return false;
        }
    }
    
    /**
     * Retourne les providers IA disponibles.
     * 
     * @return Liste des providers opérationnels
     */
    public List<IAProvider> getAvailableProviders() {
        return providerFactory.getAvailableProviders();
    }
    
    /**
     * Vérifie si au moins une IA est disponible.
     * 
     * @return true si IA disponible
     */
    public boolean hasAnyProviderAvailable() {
        return providerFactory.hasAnyProvider();
    }
    
    // ==================== Méthodes privées ====================
    
    @SuppressWarnings("unchecked")
    private <I, O> IATransformation<I, O> findTransformation(String name) throws IAException {
        Optional<IATransformation<?, ?>> found = transformations.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst();
        
        if (found.isEmpty()) {
            throw new IAException("Transformation not found: " + name);
        }
        
        return (IATransformation<I, O>) found.get();
    }
    
    private TransformationContext buildContext(LawDocumentEntity document, 
                                               boolean requiresVision, 
                                               int estimatedSize) {
        IAProvider provider = providerFactory.selectProvider(
                requiresVision, 
                estimatedSize / 4  // Rough tokens estimation
        );
        
        TransformationContext.TransformationConfig config = 
                TransformationContext.TransformationConfig.builder()
                        .temperature(0.1)
                        .maxTokens(4000)
                        .chunkSize(2000)
                        .chunkOverlap(200)
                        .timeoutSeconds(300)
                        .build();
        
        return TransformationContext.builder()
                .document(document)
                .provider(provider)
                .config(config)
                .build();
    }
}
