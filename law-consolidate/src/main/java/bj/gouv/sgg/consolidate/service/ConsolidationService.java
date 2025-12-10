package bj.gouv.sgg.consolidate.service;

import bj.gouv.sgg.consolidate.model.ConsolidatedArticle;
import bj.gouv.sgg.consolidate.model.ConsolidatedMetadata;
import bj.gouv.sgg.consolidate.model.ConsolidatedSignatory;
import bj.gouv.sgg.consolidate.repository.ConsolidatedArticleRepository;
import bj.gouv.sgg.consolidate.repository.ConsolidatedMetadataRepository;
import bj.gouv.sgg.consolidate.repository.ConsolidatedSignatoryRepository;
import bj.gouv.sgg.consolidate.exception.ConsolidationException;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.service.FileStorageService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de consolidation des données JSON vers MySQL.
 * 
 * <p>
 * <b>Responsabilités</b> :
 * <ul>
 * <li>Parser fichiers JSON générés par law-ocr-json</li>
 * <li>Mapper models JSON (Article, DocumentMetadata, Signatory) vers entités
 * JPA</li>
 * <li>Persister articles, métadonnées, signataires en BD</li>
 * <li>Gérer idempotence (UPDATE si existe, INSERT sinon)</li>
 * <li>Valider données (types, contraintes, cohérence)</li>
 * </ul>
 * 
 * <p>
 * <b>Format JSON attendu</b> :
 * 
 * <pre>{@code
 * {
 *   "_metadata": {
 *     "confidence": 0.95,
 *     "source": "OCR:PROGRAMMATIC",
 *     "timestamp": "2025-12-07T16:58:19.582425Z"
 *   },
 *   "documentId": "loi-2024-15",
 *   "type": "loi",
 *   "year": 2024,
 *   "number": 15,
 *   "promulgationDate": "2024-04-29",
 *   "promulgationCity": "Cotonou",
 *   "articles": [
 *     {"index": 1, "content": "Article 1er : ..."},
 *     {"index": 2, "content": "Article 2 : ..."}
 *   ],
 *   "signatories": [
 *     {"role": "Président de la République", "name": "Patrice TALON"}
 *   ]
 * }
 * }</pre>
 * 
 * <p>
 * <b>Idempotence</b> : La méthode {@link #consolidateDocument(LawDocument)}
 * est idempotente. Si le document existe déjà, ses données sont mises à jour.
 * 
 * @see ConsolidatedArticle
 * @see ConsolidatedMetadata
 * @see ConsolidatedSignatory
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsolidationService {

    private final FileStorageService fileStorageService;
    private final ConsolidatedArticleRepository articleRepository;
    private final ConsolidatedMetadataRepository metadataRepository;
    private final ConsolidatedSignatoryRepository signatoryRepository;
    private final Gson gson;

    /**
     * Consolide un document : charge JSON, parse, persiste en BD.
     * 
     * <p>
     * <b>Logique de confiance</b> : Si le document est déjà consolidé,
     * la consolidation n'est effectuée QUE SI la nouvelle confiance est
     * supérieure à l'existante. Sinon, les données existantes sont conservées.
     * 
     * @param document Document à consolider (doit avoir status EXTRACTED)
     * @return true si consolidé/mis à jour, false si skip (confiance inférieure)
     * @throws ConsolidationException Si erreur parsing JSON ou persistance BD
     */
    @Transactional
    public boolean consolidateDocument(LawDocument document) {
        String docId = document.getDocumentId();

        try {
            log.info("🔄 [{}] Démarrage consolidation...", docId);

            // 1. Charger JSON depuis filesystem
            Path jsonPath = fileStorageService.jsonPath(document.getType(), docId);

            if (!Files.exists(jsonPath)) {
                throw new ConsolidationException(
                        String.format("JSON file not found: %s", jsonPath));
            }

            String jsonContent = Files.readString(jsonPath);
            JsonObject jsonDoc = gson.fromJson(jsonContent, JsonObject.class);

            // 2. Parser métadonnées extraction
            JsonObject metadata = jsonDoc.getAsJsonObject("_metadata");
            double confidence = metadata.get("confidence").getAsDouble();
            String source = metadata.get("source").getAsString();
            String timestamp = metadata.get("timestamp").getAsString();

            log.debug("📊 [{}] Confiance: {}, Source: {}", docId, confidence, source);

            // 3. Vérifier confiance existante (skip si inférieure)
            if (isDocumentConsolidated(docId)) {
                ConsolidatedMetadata existingMetadata = metadataRepository.findByDocumentId(docId).orElse(null);
                if (existingMetadata != null && existingMetadata.getExtractionConfidence() >= confidence) {
                    log.info("⏭️ [{}] Confiance existante ({}) >= nouvelle ({}), skip",
                            docId, existingMetadata.getExtractionConfidence(), confidence);
                    return false;
                }
                log.info("🔄 [{}] Confiance supérieure ({} > {}), mise à jour...",
                        docId, confidence, existingMetadata != null ? existingMetadata.getExtractionConfidence() : 0.0);
            }

            // 4. Consolider métadonnées document
            consolidateMetadata(jsonDoc, document, confidence, source, timestamp);

            // 5. Consolider articles
            JsonArray articles = jsonDoc.getAsJsonArray("articles");
            consolidateArticles(articles, document, confidence, source);

            // 6. Consolider signataires (si présents)
            if (jsonDoc.has("signatories") && !jsonDoc.get("signatories").isJsonNull()) {
                JsonArray signatories = jsonDoc.getAsJsonArray("signatories");
                consolidateSignatories(signatories, document);
            }

            log.info("✅ [{}] Consolidation terminée: {} articles, {} signataires",
                    docId, articles.size(),
                    jsonDoc.has("signatories") ? jsonDoc.getAsJsonArray("signatories").size() : 0);

            return true;

        } catch (IOException e) {
            log.error("❌ [{}] Erreur lecture JSON: {}", docId, e.getMessage());
            throw new ConsolidationException("Failed to read JSON file: " + docId, e);
        } catch (Exception e) {
            log.error("❌ [{}] Erreur consolidation: {}", docId, e.getMessage(), e);
            throw new ConsolidationException("Failed to consolidate document: " + docId, e);
        }
    }

    /**
     * Consolide les métadonnées d'un document.
     * Idempotent : UPDATE si existe, INSERT sinon.
     */
    private void consolidateMetadata(JsonObject jsonDoc, LawDocument document,
            double confidence, String source, String timestamp) {
        String docId = document.getDocumentId();

        // Récupérer ou créer metadata
        ConsolidatedMetadata metadata = metadataRepository.findByDocumentId(docId)
                .orElse(ConsolidatedMetadata.builder()
                        .documentId(docId)
                        .build());

        // Mapper champs
        metadata.setDocumentType(document.getType());
        metadata.setDocumentYear(document.getYear());
        metadata.setDocumentNumber(document.getNumber());

        // Titre (optionnel)
        if (jsonDoc.has("title") && !jsonDoc.get("title").isJsonNull()) {
            metadata.setTitle(jsonDoc.get("title").getAsString());
        }

        // Date promulgation (optionnel)
        if (jsonDoc.has("promulgationDate") && !jsonDoc.get("promulgationDate").isJsonNull()) {
            metadata.setPromulgationDate(jsonDoc.get("promulgationDate").getAsString());
        }

        // Ville promulgation (optionnel)
        if (jsonDoc.has("promulgationCity") && !jsonDoc.get("promulgationCity").isJsonNull()) {
            metadata.setPromulgationCity(jsonDoc.get("promulgationCity").getAsString());
        }

        // Nombre d'articles
        if (jsonDoc.has("articles") && !jsonDoc.get("articles").isJsonNull()) {
            metadata.setTotalArticles(jsonDoc.getAsJsonArray("articles").size());
        }

        // URL source
        if (document.getUrl() != null) {
            metadata.setSourceUrl(document.getUrl());
        }

        // Métadonnées extraction
        metadata.setExtractionConfidence(confidence);
        metadata.setExtractionMethod(source);
        metadata.setExtractionTimestamp(timestamp);
        metadata.setConsolidatedAt(LocalDateTime.now());

        metadataRepository.save(metadata);
        log.debug("💾 [{}] Métadonnées sauvegardées", docId);
    }

    /**
     * Consolide les articles d'un document.
     * Idempotent : UPDATE si existe, INSERT sinon.
     */
    private void consolidateArticles(JsonArray articles, LawDocument document,
            double confidence, String source) {
        String docId = document.getDocumentId();
        List<ConsolidatedArticle> consolidatedArticles = new ArrayList<>();

        for (int i = 0; i < articles.size(); i++) {
            JsonObject articleJson = articles.get(i).getAsJsonObject();
            int index = articleJson.get("index").getAsInt();
            String content = articleJson.get("content").getAsString();

            // Récupérer ou créer article
            ConsolidatedArticle article = articleRepository
                    .findByDocumentIdAndArticleIndex(docId, index)
                    .orElse(ConsolidatedArticle.builder()
                            .documentId(docId)
                            .articleIndex(index)
                            .build());

            // Mapper champs
            article.setContent(content);
            article.setDocumentType(document.getType());
            article.setDocumentYear(document.getYear());
            article.setDocumentNumber(document.getNumber());
            article.setExtractionConfidence(confidence);
            article.setExtractionMethod(source);
            article.setConsolidatedAt(LocalDateTime.now());

            consolidatedArticles.add(article);
        }

        // Batch save pour performance
        articleRepository.saveAll(consolidatedArticles);
        log.debug("💾 [{}] {} articles sauvegardés", docId, consolidatedArticles.size());
    }

    /**
     * Consolide les signataires d'un document.
     * Idempotent : UPDATE si existe, INSERT sinon.
     */
    private void consolidateSignatories(JsonArray signatories, LawDocument document) {
        String docId = document.getDocumentId();
        List<ConsolidatedSignatory> consolidatedSignatories = new ArrayList<>();

        for (int i = 0; i < signatories.size(); i++) {
            JsonObject signatoryJson = signatories.get(i).getAsJsonObject();
            int order = i + 1; // Ordre d'apparition (1-based)

            String role = signatoryJson.get("role").getAsString();
            String name = signatoryJson.get("name").getAsString();

            // Récupérer ou créer signataire
            ConsolidatedSignatory signatory = signatoryRepository
                    .findByDocumentIdAndSignatoryOrder(docId, order)
                    .orElse(ConsolidatedSignatory.builder()
                            .documentId(docId)
                            .signatoryOrder(order)
                            .build());

            // Mapper champs
            signatory.setRole(role);
            signatory.setName(name);
            signatory.setDocumentType(document.getType());
            signatory.setDocumentYear(document.getYear());
            signatory.setConsolidatedAt(LocalDateTime.now());

            // Dates mandat (optionnelles)
            if (signatoryJson.has("mandateStart") && !signatoryJson.get("mandateStart").isJsonNull()) {
                signatory.setMandateStart(LocalDate.parse(signatoryJson.get("mandateStart").getAsString()));
            }
            if (signatoryJson.has("mandateEnd") && !signatoryJson.get("mandateEnd").isJsonNull()) {
                signatory.setMandateEnd(LocalDate.parse(signatoryJson.get("mandateEnd").getAsString()));
            }

            consolidatedSignatories.add(signatory);
        }

        // Batch save pour performance
        signatoryRepository.saveAll(consolidatedSignatories);
        log.debug("💾 [{}] {} signataires sauvegardés", docId, consolidatedSignatories.size());
    }

    /**
     * Vérifie si un document est déjà consolidé.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     * @return true si consolidé (métadonnées existent), false sinon
     */
    public boolean isDocumentConsolidated(String documentId) {
        return metadataRepository.existsByDocumentId(documentId);
    }

    /**
     * Supprime toutes les données consolidées d'un document.
     * Utile pour re-consolidation complète.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     */
    @Transactional
    public void deleteConsolidatedDocument(String documentId) {
        log.warn("🗑️ [{}] Suppression données consolidées...", documentId);

        articleRepository.deleteByDocumentId(documentId);
        signatoryRepository.deleteByDocumentId(documentId);
        metadataRepository.deleteByDocumentId(documentId);

        log.info("✅ [{}] Données consolidées supprimées", documentId);
    }
}
