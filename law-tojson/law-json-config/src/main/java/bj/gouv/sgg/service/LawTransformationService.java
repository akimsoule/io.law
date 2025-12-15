package bj.gouv.sgg.service;

import bj.gouv.sgg.ai.service.AIOrchestrator;
import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.qa.service.JsonQualityService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Service orchestrateur pour la transformation PDF → JSON avec stratégie de fallback en cascade.
 * 
 * <p><b>Pipeline de Transformation (avec checks qualité)</b> :
 * <pre>
 * 1. Extraction OCR + Corrections CSV
 *    ├─ Check qualité OCR (law-qa)
 *    ├─ SI mauvaise qualité → AI Correction OCR
 *    └─ SI toujours mauvais → Continuer quand même
 * 
 * 2. Extraction Articles depuis OCR corrigé
 *    ├─ Check qualité JSON (law-qa)
 *    ├─ SI mauvaise qualité → AI Correction JSON
 *    └─ SI toujours mauvais → Fallback AI complet
 * 
 * 3. Fallback AI Complet
 *    ├─ AI extraction directe PDF → JSON
 *    ├─ Check qualité JSON final
 *    └─ SI toujours mauvais → FAILED
 * 
 * 4. Statut Final
 *    ├─ SI qualité >= seuil → SUCCESS
 *    └─ SINON → FAILED (skip traitement)
 * </pre>
 * 
 * <p><b>Seuils de Qualité</b> :
 * <ul>
 *   <li>OCR : {@code law.quality.ocr-threshold} (défaut: 0.3)</li>
 *   <li>JSON : {@code law.quality.json-threshold} (défaut: 0.5)</li>
 * </ul>
 * 
 * @see OcrTransformer
 * @see AIOrchestrator
 * @see JsonQualityService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LawTransformationService {

    private final OcrTransformer ocrTransformer;
    private final AIOrchestrator aiOrchestrator;
    private final JsonQualityService jsonQualityService;
    private final FileStorageService fileStorageService;
    private final Gson gson;
    
    @Value("${law.quality.ocr-threshold:0.3}")
    private double ocrQualityThreshold;
    
    @Value("${law.quality.json-threshold:0.5}")
    private double jsonQualityThreshold;
    
    private static final String LOG_SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    
    /**
     * Transforme un PDF en JSON avec stratégie de fallback intelligente.
     * 
     * @param document Document à transformer
     * @param pdfPath Chemin du fichier PDF
     * @return JsonResult avec JSON structuré et confiance
     * @throws IAException Si toutes les stratégies échouent
     */
    public JsonResult transform(LawDocument document, Path pdfPath) throws IAException {
        String docId = document.getDocumentId();
        log.info(LOG_SEPARATOR);
        log.info("🚀 [{}] Démarrage transformation PDF → JSON avec fallback cascade", docId);
        log.info(LOG_SEPARATOR);
        
        // ÉTAPE 1 : Extraction OCR de base + Corrections CSV
        JsonResult ocrResult = transformWithOcr(document, pdfPath);
        
        // Check qualité OCR
        double ocrConfidence = ocrResult.getConfidence();
        log.info("🎯 [{}] Confiance OCR brut: {} (seuil: {})", docId, ocrConfidence, ocrQualityThreshold);
        
        JsonResult currentResult = ocrResult;
        
        // ÉTAPE 2 : Si OCR mauvaise qualité → AI Correction OCR
        if (ocrConfidence < ocrQualityThreshold) {
            log.warn("⚠️ [{}] Confiance OCR < seuil → Tentative AI correction OCR", docId);
            try {
                JsonResult aiOcrResult = transformWithAiOcrCorrection(document);
                if (aiOcrResult.getConfidence() > currentResult.getConfidence()) {
                    log.info("✅ [{}] AI correction OCR améliore confiance: {} → {}", 
                             docId, currentResult.getConfidence(), aiOcrResult.getConfidence());
                    currentResult = aiOcrResult;
                } else {
                    log.info("⏭️ [{}] AI correction OCR n'améliore pas, garder OCR brut", docId);
                }
            } catch (Exception e) {
                log.warn("⚠️ [{}] AI correction OCR échouée: {}, continuer avec OCR brut", docId, e.getMessage());
            }
        } else {
            log.info("✅ [{}] OCR confiance OK, skip AI correction OCR", docId);
        }
        
        // ÉTAPE 3 : Check qualité JSON
        double jsonQuality = calculateJsonQuality(currentResult.getJson());
        log.info("📊 [{}] Qualité JSON: {} (seuil: {})", docId, jsonQuality, jsonQualityThreshold);
        
        // ÉTAPE 4 : Si JSON mauvaise qualité → AI Correction JSON
        if (jsonQuality < jsonQualityThreshold) {
            log.warn("⚠️ [{}] Qualité JSON < seuil → Tentative AI correction JSON", docId);
            try {
                JsonResult aiJsonResult = transformWithAiJsonCorrection(document, currentResult);
                double aiJsonQuality = calculateJsonQuality(aiJsonResult.getJson());
                
                if (aiJsonQuality > jsonQuality) {
                    log.info("✅ [{}] AI correction JSON améliore qualité: {} → {}", 
                             docId, jsonQuality, aiJsonQuality);
                    currentResult = aiJsonResult;
                    jsonQuality = aiJsonQuality;
                } else {
                    log.info("⏭️ [{}] AI correction JSON n'améliore pas", docId);
                }
            } catch (Exception e) {
                log.warn("⚠️ [{}] AI correction JSON échouée: {}", docId, e.getMessage());
            }
        } else {
            log.info("✅ [{}] JSON qualité OK, skip AI correction JSON", docId);
        }
        
        // ÉTAPE 5 : Si toujours mauvaise qualité → Fallback AI complet (PDF direct)
        if (jsonQuality < jsonQualityThreshold) {
            log.warn("⚠️ [{}] Qualité JSON toujours < seuil → Fallback AI extraction complète", docId);
            try {
                JsonResult aiFullResult = transformWithAiFull(document, pdfPath);
                double aiFullQuality = calculateJsonQuality(aiFullResult.getJson());
                
                if (aiFullQuality > jsonQuality) {
                    log.info("✅ [{}] AI extraction complète améliore qualité: {} → {}", 
                             docId, jsonQuality, aiFullQuality);
                    currentResult = aiFullResult;
                    jsonQuality = aiFullQuality;
                } else {
                    log.warn("⚠️ [{}] AI extraction complète n'améliore pas", docId);
                }
            } catch (Exception e) {
                log.error("❌ [{}] AI extraction complète échouée: {}", docId, e.getMessage());
            }
        }
        
        // ÉTAPE 6 : Vérification finale
        if (jsonQuality < jsonQualityThreshold) {
            log.error(LOG_SEPARATOR);
            log.error("❌ [{}] ÉCHEC : Qualité JSON finale insuffisante: {}", docId, jsonQuality);
            log.error("❌ [{}] Document marqué FAILED, skip traitement", docId);
            log.error(LOG_SEPARATOR);
            throw new IAException(String.format(
                "[%s] Qualité JSON finale insuffisante: %.2f < %.2f. Toutes stratégies échouées.",
                docId, jsonQuality, jsonQualityThreshold
            ));
        }
        
        log.info(LOG_SEPARATOR);
        log.info("✅ [{}] Transformation réussie avec qualité JSON: {}", docId, jsonQuality);
        log.info("🎯 [{}] Confiance finale: {}, Source: {}", 
                 docId, currentResult.getConfidence(), currentResult.getSource());
        log.info(LOG_SEPARATOR);
        
        return currentResult;
    }
    
    /**
     * ÉTAPE 1 : Extraction OCR de base + Corrections CSV.
     */
    private JsonResult transformWithOcr(LawDocument document, Path pdfPath) throws IAException {
        String docId = document.getDocumentId();
        log.info("▶️  1️⃣ [{}] Extraction OCR + Corrections CSV", docId);
        
        JsonResult result = ocrTransformer.transform(document, pdfPath);
        log.info("✅ [{}] OCR extraction: {} articles, confiance {}", 
                 docId, extractArticleCount(result), result.getConfidence());
        
        return result;
    }
    
    /**
     * ÉTAPE 2 : AI Correction du texte OCR brut.
     * 
     * <p>Stratégie : L'IA corrige les erreurs OCR AVANT l'extraction des articles.
     */
    private JsonResult transformWithAiOcrCorrection(LawDocument document) throws IAException {
        String docId = document.getDocumentId();
        log.info("▶️  2️⃣ [{}] AI Correction OCR", docId);
        
        // Lire le texte OCR brut depuis le fichier sauvegardé
        String ocrText = readOcrText(document);
        
        // Corriger OCR via AI
        String correctedOcr = aiOrchestrator.correctOcr(document, ocrText);
        
        // Extraire JSON depuis OCR corrigé
        JsonObject jsonObject = aiOrchestrator.ocrToJson(document, correctedOcr);
        String jsonString = gson.toJson(jsonObject);
        
        // Calculer confiance (90% car AI + correction)
        double confidence = 0.90;
        String source = "AI:CORRECTED_OCR";
        
        JsonResult aiResult = new JsonResult(jsonString, confidence, source);
        log.info("✅ [{}] AI correction OCR: {} articles, confiance {}", 
                 docId, extractArticleCount(aiResult), aiResult.getConfidence());
        
        return aiResult;
    }
    
    /**
     * ÉTAPE 3 : AI Correction du JSON extrait.
     * 
     * <p>Stratégie : L'IA améliore le JSON déjà extrait (complète métadonnées manquantes, etc.).
     * <p><b>TODO</b> : Implémenter via AIOrchestrator.correctJson().
     */
    private JsonResult transformWithAiJsonCorrection(LawDocument document, JsonResult currentResult) throws IAException {
        String docId = document.getDocumentId();
        log.info("▶️  3️⃣ [{}] AI Correction JSON", docId);
        
        // Pour l'instant, on retourne le résultat actuel sans correction
        log.warn("⚠️ [{}] AI correction JSON non implémentée, skip", docId);
        return currentResult;
    }
    
    /**
     * ÉTAPE 4 : AI Extraction complète (PDF direct → JSON).
     * 
     * <p>Stratégie : L'IA lit le PDF directement et génère le JSON complet.
     * <p><b>TODO</b> : Implémenter via AIOrchestrator.pdfToJson().
     */
    @SuppressWarnings("unused")
    private JsonResult transformWithAiFull(LawDocument document, Path pdfPath) throws IAException {
        String docId = document.getDocumentId();
        log.info("▶️  4️⃣ [{}] AI Extraction Complète (PDF → JSON direct)", docId);
        
        // Pour l'instant, on lève une exception
        throw new IAException("[" + docId + "] AI extraction complète non implémentée");
    }
    
    /**
     * Calcule la qualité globale du JSON via law-qa.
     */
    private double calculateJsonQuality(String jsonContent) {
        try {
            return jsonQualityService.calculateJsonQualityScore(jsonContent);
        } catch (Exception e) {
            log.warn("⚠️ Erreur calcul qualité JSON: {}, retour 0.0", e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Lit le texte OCR depuis le fichier disque.
     */
    private String readOcrText(LawDocument document) throws IAException {
        try {
            return fileStorageService.readOcr(document.getType(), document.getDocumentId());
        } catch (Exception e) {
            throw new IAException("Impossible de lire le fichier OCR: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extrait le nombre d'articles depuis le JSON.
     */
    private int extractArticleCount(JsonResult result) {
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(result.getJson()).getAsJsonObject();
            if (root.has("articles")) {
                return root.getAsJsonArray("articles").size();
            }
        } catch (Exception e) {
            log.warn("⚠️ Impossible d'extraire le nombre d'articles: {}", e.getMessage());
        }
        return 0;
    }
}
