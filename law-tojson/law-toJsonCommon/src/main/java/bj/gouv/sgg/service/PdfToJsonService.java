package bj.gouv.sgg.service;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.transformer.PdfToJsonTransformer;
import bj.gouv.sgg.util.IaAvailabilityChecker;
import bj.gouv.sgg.util.MachineCapacityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PdfToJsonService {
    private final LawProperties properties;
    private final PdfToJsonTransformer iaTransformer; // bean impl (Ollama/Groq orchestré)
    private final PdfToJsonTransformer ocrTransformer; // bean impl (OCR → JSON)
    private final FileStorageService storageService;

    public PdfToJsonTransformer.JsonResult process(LawDocument doc, Path pdfPath, Optional<PdfToJsonTransformer.JsonResult> existingJson) {
        // Stratégie: IA par défaut (Ollama→Groq), fallback OCR si capacité IA non remplie ou indisponible
        boolean iaUsable = MachineCapacityUtil.isIaCapable(properties) && IaAvailabilityChecker.isIaAvailable(properties);
        boolean ocrUsable = MachineCapacityUtil.isOcrCapable(properties);

        if (iaUsable) {
            try {
                var result = iaTransformer.transform(doc, pdfPath);
                return pickBetter(existingJson, result);
            } catch (java.io.IOException e) {
                throw new bj.gouv.sgg.exception.JsonProcessingException("IA transformation failed", e);
            }
        }
        if (ocrUsable) {
            try {
                var result = ocrTransformer.transform(doc, pdfPath);
                return pickBetter(existingJson, result);
            } catch (java.io.IOException e) {
                throw new bj.gouv.sgg.exception.JsonProcessingException("OCR transformation failed", e);
            }
        }
        // Aucun mode disponible: retourner existant ou erreur
        return existingJson.orElseThrow(() -> new IllegalStateException("Aucune capacité disponible pour PDF→JSON"));
    }

    /**
     * Idempotence: garder existant si confiance >= candidate, sinon remplacer
     * Cela évite d'écraser un bon résultat par un moins bon
     */
    private PdfToJsonTransformer.JsonResult pickBetter(Optional<PdfToJsonTransformer.JsonResult> existing,
                                                       PdfToJsonTransformer.JsonResult candidate) {
        if (existing.isEmpty()) {
            return candidate;
        }
        
        PdfToJsonTransformer.JsonResult existingResult = existing.get();
        // Ne remplacer que si le nouveau résultat est significativement mieux (> 0.1 de différence)
        if (existingResult.getConfidence() >= candidate.getConfidence() - 0.1) {
            return existingResult;  // Garder existant (idempotent)
        }
        
        return candidate;  // Remplacer par meilleur
    }
}
