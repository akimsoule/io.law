package bj.gouv.sgg.service;

import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.exception.PromptLoadException;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.modele.JsonResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public interface IAService extends ExtractToJson, LoadPrompt {

    Logger LOGGER = LoggerFactory.getLogger(IAService.class);

    /**
     * Génère le texte via l'API IA avec images base64 (pour PDFs scannés)
     */
    String generateTextWithImages(String prompt, String systemPrompt, List<String> imagesBase64) throws IAException;

    /**
     * Retourne le nom de la source IA (ex: "IA:GROQ", "IA:OLLAMA")
     */
    String getSourceName();
    
    /**
     * Vérifie si le service IA est disponible et opérationnel.
     * 
     * <p><b>Implémentations</b> :
     * <ul>
     *   <li><b>OllamaClient</b> : Ping Ollama + vérifier modèle disponible</li>
     *   <li><b>GroqClient</b> : Vérifier API key configurée + serveur accessible</li>
     *   <li><b>NoClient</b> : Toujours false (pas d'IA)</li>
     * </ul>
     * 
     * @return true si le service est disponible, false sinon
     */
    boolean isAvailable();

    /**
     * Logique commune de transformation PDF→JSON pour tous les clients IA
     */
    default JsonResult commonTransform(LawDocument document, Path pdfPath) throws IAException {
        String sourceName = getSourceName();
        
        if (!Files.exists(pdfPath)) {
            return new JsonResult(
                    "{\"documentId\":\"" + document.getDocumentId() + "\",\"articles\":[]}",
                    0.2,
                    sourceName + ":FILE_NOT_FOUND"
            );
        }

        try {
            // 1. Charger le prompt adapté
            String promptFilename = "decret".equals(document.getType())
                    ? "decret-parser.txt"
                    : "pdf-parser.txt";
            String promptTemplate = loadPrompt(promptFilename);

            if (promptTemplate.isBlank()) {
                throw new PromptLoadException("Cannot load " + promptFilename + " prompt");
            }

            // 2. Convertir PDF en images base64
            List<String> imagesBase64 = convertPdfToBase64Images(pdfPath);

            // 3. Formatter le prompt (sans {text} car on envoie des images)
            String prompt = promptTemplate.replace("{text}", 
                    "Analyser les images suivantes (" + imagesBase64.size() + " pages) et extraire les informations juridiques.");

            // 5. Appeler l'API IA avec images base64
            String responseText = generateTextWithImages(prompt, null, imagesBase64);
            
            // 6. Nettoyer la réponse JSON
            String json = cleanJsonResponse(responseText);
            
            // 7. Calculer la confiance (estimation basée sur nombre de pages)
            int estimatedTextLength = imagesBase64.size() * 2000; // ~2000 chars par page
            double confidence = estimateConfidenceFromValidation(json, estimatedTextLength);

            return new JsonResult(json, confidence, sourceName);

        } catch (Exception e) {
            return new JsonResult(
                    "{\"documentId\":\"" + document.getDocumentId() + "\",\"error\":\"" + e.getMessage() + "\"}",
                    0.1,
                    sourceName + ":ERROR"
            );
        }
    }

    /**
     * Nettoie la réponse JSON brute pour extraire uniquement le JSON valide
     */
    default String cleanJsonResponse(String response) {
        if (response == null || response.isBlank()) {
            return "{\"articles\":[]}";
        }

        int startIdx = response.indexOf('{');
        int endIdx = response.lastIndexOf('}');

        if (startIdx >= 0 && endIdx > startIdx) {
            return response.substring(startIdx, endIdx + 1);
        }
        return "{\"articles\":[]}";
    }

    /**
     * Estime la confiance du résultat JSON basée sur sa structure et son contenu
     */
    default double estimateConfidenceFromValidation(String json, int sourceTextLength) {
        double baseScore = 0.5;

        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            if (obj.has("documentId")) baseScore += 0.15;
            if (obj.has("type")) baseScore += 0.1;
            if (obj.has("title")) baseScore += 0.1;

            if (obj.has("articles") && obj.get("articles").isJsonArray()) {
                int articleCount = obj.getAsJsonArray("articles").size();
                baseScore += 0.2;
                if (articleCount > 0) baseScore += 0.15;
                if (articleCount >= 3) baseScore += 0.1;
            }

            if (json.length() < 100) {
                baseScore = Math.max(0.15, baseScore - 0.3);
            }

            if (sourceTextLength > 2000) {
                baseScore = Math.min(0.95, baseScore + 0.1);
            }

        } catch (Exception e) {
            baseScore = 0.2;
        }

        return Math.max(0.15, Math.min(0.95, baseScore));
    }

    /**
     * Convertit un PDF en liste d'images base64 (PNG format)
     * Chaque page du PDF devient une image PNG encodée en base64
     */
    default List<String> convertPdfToBase64Images(Path pdfPath) throws IAException {
        List<String> imagesBase64 = new ArrayList<>();
        
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            org.apache.pdfbox.text.PDFTextStripper textStripper = new org.apache.pdfbox.text.PDFTextStripper();
            int pageCount = document.getNumberOfPages();
            
            // Limiter à 20 pages pour éviter surcharge mémoire
            int maxPages = Math.min(pageCount, 20);
            
            for (int pageIndex = 0; pageIndex < maxPages; pageIndex++) {
                // Extraire le texte de la page pour détecter les annexes
                textStripper.setStartPage(pageIndex + 1);
                textStripper.setEndPage(pageIndex + 1);
                String pageText = textStripper.getText(document);
                
                // Détection marqueurs d'annexes (stop conversion)
                if (containsAnnexMarker(pageText)) {
                    LOGGER.info("⏹️ Arrêt conversion PDF à la page {} : annexe détectée (AMPLIATIONS)", pageIndex + 1);
                    break;
                }
                
                // Render page à 300 DPI pour bonne qualité OCR
                BufferedImage image = pdfRenderer.renderImageWithDPI(pageIndex, 300);
                
                // Convertir image en bytes PNG
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", baos);
                byte[] imageBytes = baos.toByteArray();
                
                // Encoder en base64
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                imagesBase64.add(base64Image);
            }
            
            LOGGER.info("✅ Conversion PDF terminée : {} pages converties sur {} totales", 
                     imagesBase64.size(), pageCount);
            
            return imagesBase64;
            
        } catch (Exception e) {
            throw new IAException("Failed to convert PDF to base64 images: " + e.getMessage(), e);
        }
    }

    /**
     * Détecte si une page contient un marqueur d'annexe
     * Marqueurs détectés : AMPLIATIONS, ANNEXE, ANNEXES
     */
    default boolean containsAnnexMarker(String pageText) {
        if (pageText == null || pageText.trim().isEmpty()) {
            return false;
        }
        
        String upperText = pageText.toUpperCase();
        
        // Marqueurs d'annexes (case-insensitive)
        return upperText.contains("AMPLIATIONS") 
            || upperText.contains("ANNEXE") 
            || upperText.contains("ANNEXES");
    }

}
