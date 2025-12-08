package bj.gouv.sgg.impl;

import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.exception.PromptLoadException;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.service.IAService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Implémentation No-Op pour IAService
 * Utilisée quand aucun provider IA n'est disponible
 */
@Slf4j
@Component
public class NoClient implements IAService {

    @Override
    public String getSourceName() {
        return "IA:NONE";
    }
    
    @Override
    public boolean isAvailable() {
        // NoClient n'est jamais "disponible" (pas d'IA)
        return false;
    }

    @Override
    public String generateTextWithImages(String prompt, String systemPrompt, List<String> imagesBase64) throws IAException {
        log.warn("⚠️ NoClient.generateTextWithImages called: IA disabled");
        throw new IAException("AI disabled - no provider available");
    }

    @Override
    public JsonResult transform(LawDocument document, Path pdfPath) throws IAException {
        log.warn("⚠️ NoClient called for document {}: IA disabled", document.getDocumentId());
        throw new IAException("AI disabled - no provider available");
    }

    @Override
    public String loadPrompt(String filename) throws PromptLoadException {
        log.warn("⚠️ NoClient.loadPrompt called for {}: IA disabled", filename);
        throw new PromptLoadException("AI disabled - no provider available");
    }
}
