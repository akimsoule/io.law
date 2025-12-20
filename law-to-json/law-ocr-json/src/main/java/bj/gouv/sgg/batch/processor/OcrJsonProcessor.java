package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.exception.OcrExtractionException;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.DocumentMetadata;
import bj.gouv.sgg.model.OcrExtractionResult;
import bj.gouv.sgg.service.FileStorageService;
import bj.gouv.sgg.service.extract.impl.OcrExtractionServiceImpl;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Processor Spring Batch pour extraction JSON depuis OCR.
 * 
 * <p>Workflow :
 * <ol>
 *   <li>Lit le fichier OCR (.txt)</li>
 *   <li>Extrait articles et métadonnées via OcrExtractionServiceImpl</li>
 *   <li>Génère le JSON structuré avec Gson</li>
 *   <li>Sauvegarde le fichier JSON</li>
 *   <li>Met à jour l'entité (jsonPath, status)</li>
 * </ol>
 * 
 * <p>Stateless : Ne garde aucun état entre les items
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OcrJsonProcessor implements ItemProcessor<LawDocumentEntity, LawDocumentEntity> {

    private final FileStorageService fileStorageService;
    private final ArticleExtractorConfig articleExtractorConfig;

    // Gson avec adapter pour LocalDate
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();

    /**
     * Adapter pour sérialiser/désérialiser LocalDate en ISO format
     */
    private static class LocalDateAdapter extends TypeAdapter<LocalDate> {
        @Override
        public void write(JsonWriter out, LocalDate date) throws IOException {
            if (date == null) {
                out.nullValue();
            } else {
                out.value(date.toString()); // ISO format: yyyy-MM-dd
            }
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            String dateStr = in.nextString();
            return dateStr != null ? LocalDate.parse(dateStr) : null;
        }
    }

    @Override
    public LawDocumentEntity process(LawDocumentEntity document) throws Exception {
        String documentId = document.getDocumentId();
        log.debug("⚙️ Processing JSON extraction: {}", documentId);

        try {
            // 1. Vérifier que le fichier OCR existe
            if (document.getOcrPath() == null || document.getOcrPath().isEmpty()) {
                throw new OcrExtractionException("ocrPath is null or empty for document: " + documentId);
            }

            File ocrFile = new File(document.getOcrPath());
            if (!ocrFile.exists()) {
                throw new OcrExtractionException("OCR file not found: " + document.getOcrPath());
            }

            // 2. Lire le texte OCR
            String ocrText = Files.readString(ocrFile.toPath());
            if (ocrText.trim().isEmpty()) {
                throw new OcrExtractionException("OCR text is empty for document: " + documentId);
            }

            // 3. Extraire articles et métadonnées via OcrExtractionServiceImpl
            OcrExtractionServiceImpl extractionService = new OcrExtractionServiceImpl(articleExtractorConfig);
            
            List<Article> articles = extractionService.extractArticles(ocrText);
            if (articles.isEmpty()) {
                throw new OcrExtractionException("No articles extracted for document: " + documentId);
            }

            DocumentMetadata metadata = extractionService.extractMetadata(ocrText);
            double confidence = extractionService.calculateConfidence(ocrText, articles);

            // 4. Créer le résultat structuré
            OcrExtractionResult result = OcrExtractionResult.builder()
                    .articles(articles)
                    .metadata(metadata)
                    .confidence(confidence)
                    .method("OCR")
                    .timestamp(java.time.Instant.now().toString())
                    .build();

            // 5. Générer le chemin JSON et créer le répertoire parent
            Path jsonPath = fileStorageService.jsonPath(document.getType(), documentId);
            jsonPath.getParent().toFile().mkdirs();

            // 6. Sauvegarder le JSON
            String jsonContent = gson.toJson(result);
            Files.writeString(jsonPath, jsonContent);

            // 7. Vérifier que le fichier a été créé
            if (!jsonPath.toFile().exists() || jsonPath.toFile().length() == 0) {
                throw new OcrExtractionException("JSON file empty or not created for document: " + documentId);
            }

            log.info("✅ JSON extracted: {} ({} articles, confidence: {:.2f}, path: {})",
                    documentId, articles.size(), confidence, jsonPath);

            // 8. Mettre à jour l'entité
            document.setJsonPath(jsonPath.toString());
            document.setStatus(ProcessingStatus.EXTRACTED);
            document.setErrorMessage(null);

            return document;

        } catch (OcrExtractionException e) {
            log.error("❌ JSON extraction failed for {}: {}", documentId, e.getMessage());
            document.setStatus(ProcessingStatus.FAILED_EXTRACTION);
            document.setErrorMessage("JSON extraction failed: " + e.getMessage());
            return document;

        } catch (IOException e) {
            log.error("❌ I/O error for {}: {}", documentId, e.getMessage());
            document.setStatus(ProcessingStatus.FAILED_EXTRACTION);
            document.setErrorMessage("I/O error: " + e.getMessage());
            return document;

        } catch (Exception e) {
            log.error("❌ Unexpected error for {}: {}", documentId, e.getMessage(), e);
            document.setStatus(ProcessingStatus.FAILED_EXTRACTION);
            document.setErrorMessage("Unexpected error: " + e.getMessage());
            return document;
        }
    }
}
