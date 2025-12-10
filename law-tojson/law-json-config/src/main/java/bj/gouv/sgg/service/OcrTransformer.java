package bj.gouv.sgg.service;

import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.impl.ArticleRegexExtractor;
import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.DocumentMetadata;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.modele.JsonResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Transformer qui combine OcrService + ArticleRegexExtractor
 * pour produire un JSON structuré depuis un PDF.
 * 
 * <p><b>Pipeline</b> :
 * <ol>
 *   <li>PDF → Texte OCR (via OcrService)</li>
 *   <li>Texte → Articles + Métadonnées (via ArticleRegexExtractor)</li>
 *   <li>Articles + Métadonnées → JSON (via Gson)</li>
 * </ol>
 * 
 * <p><b>Source</b> : "OCR:PROGRAMMATIC"
 * <p><b>Confiance</b> : Calculée par ArticleRegexExtractor (dictionnaire français + termes juridiques)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OcrTransformer {

    private final OcrService ocrService;
    private final ArticleRegexExtractor articleRegexExtractor;
    private final FileStorageService fileStorageService;
    private final ArticleExtractorConfig articleExtractorConfig;
    
    // Gson formatté pour sortie lisible
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static final String SOURCE_OCR = "OCR:PROGRAMMATIC";
    
    /**
     * Transforme un PDF en JSON structuré via OCR programmatique.
     * 
     * @param document Document à transformer
     * @param pdfPath Chemin du fichier PDF
     * @return JsonResult avec articles extraits
     * @throws IAException si erreur OCR ou parsing
     */
    public JsonResult transform(LawDocument document, Path pdfPath) throws IAException {
        String docId = document.getDocumentId();
        log.info("🔄 [{}] Démarrage transformation OCR programmatique", docId);
        
        try {
            // 1. Extraire texte via OCR
            byte[] pdfBytes = Files.readAllBytes(pdfPath);
            String ocrText = ocrService.extractText(pdfBytes);
            
            if (ocrText == null || ocrText.trim().isEmpty()) {
                throw new IAException("OCR returned empty text for: " + docId);
            }
            
            log.debug("📄 [{}] OCR text extracted: {} chars", docId, ocrText.length());
            
            // Sauvegarder le texte OCR brut
            try {
                fileStorageService.saveOcr(document.getType(), docId, ocrText);
                log.info("💾 [{}] OCR text saved: {} chars", docId, ocrText.length());
            } catch (IOException e) {
                log.warn("⚠️ [{}] Failed to save OCR text: {}", docId, e.getMessage());
                // Continue processing même si sauvegarde OCR échoue
            }
            
            // 2. Extraire articles via regex
            List<Article> articles = articleRegexExtractor.extractArticles(ocrText);
            log.info("📝 [{}] Extracted {} articles via regex", docId, articles.size());
            
            // 3. Extraire métadonnées
            DocumentMetadata metadata = articleRegexExtractor.extractMetadata(ocrText);
            log.debug("📋 [{}] Metadata extracted: title={}, date={}, signatories={}", 
                     docId, metadata.getLawTitle(), metadata.getPromulgationDate(), 
                     metadata.getSignatories().size());
            
            // 4. Calculer confiance avec enregistrement des mots non reconnus
            double confidence = articleRegexExtractor.calculateConfidence(ocrText, articles, docId);
            log.info("🎯 [{}] Confidence calculated: {}", docId, confidence);

            // 4.b Statistiques d'occurrences des mots non reconnus (top 10)
            try {
                var unrec = articleExtractorConfig.getUnrecognizedWords(ocrText);
                var topStats = topUnrecognizedStats(ocrText, unrec, 10);
                if (!topStats.isEmpty()) {
                    log.info("📊 [{}] Top unrecognized words (word=count): {}", docId, topStats);
                } else {
                    log.info("📊 [{}] No unrecognized words found in OCR text", docId);
                }
            } catch (Exception statsEx) {
                log.warn("⚠️ [{}] Failed to compute unrecognized word stats: {}", docId, statsEx.getMessage());
            }
            
            // 5. Construire JSON
            String json = buildJson(document, articles, metadata, confidence);
            
            log.info("✅ [{}] OCR transformation completed: {} articles, confidence {}", 
                     docId, articles.size(), confidence);
            
            return new JsonResult(json, confidence, SOURCE_OCR);
            
        } catch (IOException e) {
            log.error("❌ [{}] OCR I/O error: {}", docId, e.getMessage());
            throw new IAException("OCR transformation failed for " + docId + ": I/O error", e);
        } catch (Exception e) {
            log.error("❌ [{}] OCR transformation error: {}", docId, e.getMessage(), e);
            throw new IAException("OCR transformation failed for " + docId, e);
        }
    }
    
    /**
     * Construit le JSON final avec articles + métadonnées.
     * 
     * <p>Format :
     * <pre>{@code
     * {
     *   "_metadata": {
     *     "confidence": 0.75,
     *     "source": "OCR:PROGRAMMATIC",
     *     "timestamp": "2025-12-07T10:30:00Z"
     *   },
     *   "documentId": "loi-2024-15",
     *   "type": "loi",
     *   "year": 2024,
     *   "number": 15,
     *   "title": "Loi portant...",
     *   "promulgationDate": "2024-06-15",
     *   "promulgationCity": "Porto-Novo",
     *   "articles": [
     *     {
     *       "index": 1,
     *       "content": "Article 1er..."
     *     }
     *   ],
     *   "signatories": [
     *     {
     *       "name": "Patrice TALON",
     *       "title": "Président de la République",
     *       "order": 1
     *     }
     *   ]
     * }
     * }</pre>
     */
    private String buildJson(LawDocument document, List<Article> articles, 
                            DocumentMetadata metadata, double confidence) {
        JsonObject root = new JsonObject();
        
        // _metadata
        JsonObject metadataJson = new JsonObject();
        metadataJson.addProperty("confidence", confidence);
        metadataJson.addProperty("source", SOURCE_OCR);
        metadataJson.addProperty("timestamp", LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z");
        root.add("_metadata", metadataJson);
        
        // Document identifiers
        root.addProperty("documentId", document.getDocumentId());
        root.addProperty("type", document.getType());
        root.addProperty("year", document.getYear());
        root.addProperty("number", document.getNumber());
        
        // Metadata fields
        if (metadata.getLawTitle() != null) {
            root.addProperty("title", metadata.getLawTitle());
        }
        if (metadata.getPromulgationDate() != null) {
            root.addProperty("promulgationDate", metadata.getPromulgationDate());
        }
        if (metadata.getPromulgationCity() != null) {
            root.addProperty("promulgationCity", metadata.getPromulgationCity());
        }
        
        // Articles
        JsonArray articlesArray = new JsonArray();
        for (Article article : articles) {
            JsonObject articleJson = new JsonObject();
            articleJson.addProperty("index", article.getIndex());
            articleJson.addProperty("content", article.getContent());
            articlesArray.add(articleJson);
        }
        root.add("articles", articlesArray);
        
        // Signatories
        if (!metadata.getSignatories().isEmpty()) {
            JsonArray signatoriesArray = new JsonArray();
            for (int i = 0; i < metadata.getSignatories().size(); i++) {
                var signatory = metadata.getSignatories().get(i);
                JsonObject signatoryJson = new JsonObject();
                signatoryJson.addProperty("name", signatory.getName());
                signatoryJson.addProperty("role", signatory.getRole());
                signatoryJson.addProperty("order", i + 1);
                signatoriesArray.add(signatoryJson);
            }
            root.add("signatories", signatoriesArray);
        }
        
        return PRETTY_GSON.toJson(root);
    }

    /**
     * Calcule le top N des mots non reconnus avec leurs occurrences dans le texte OCR.
     * Retourne une chaîne compacte "mot1=12, mot2=9, ...".
     */
    private String topUnrecognizedStats(String ocrText, java.util.Set<String> words, int topN) {
        if (ocrText == null || ocrText.isBlank() || words == null || words.isEmpty()) {
            return "";
        }
        String lower = ocrText.toLowerCase();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (String w : words) {
            if (w == null || w.isBlank()) continue;
            String esc = w.toLowerCase()
                .replaceAll("([\\\\.\\^\\$\\|\\?\\*\\+\\(\\)\\[\\]\\{\\}])", "\\\\$1");
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(^|[^A-Za-zÀ-ÿ])" + esc + "([^A-Za-zÀ-ÿ]|$)");
            java.util.regex.Matcher m = p.matcher(lower);
            int c = 0;
            while (m.find()) { c++; }
            if (c > 0) counts.put(w, c);
        }
        java.util.List<java.util.Map.Entry<String,Integer>> list = new java.util.ArrayList<>(counts.entrySet());
        list.sort((a,b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(topN, list.size());
        for (int i = 0; i < limit; i++) {
            var e = list.get(i);
            if (i > 0) sb.append(", ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }
}
