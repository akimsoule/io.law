package bj.gouv.sgg.batch;

import bj.gouv.sgg.entity.LawArticleEntity;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.service.FileStorageService;
import bj.gouv.sgg.service.LawArticleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Processor pour consolidation des documents JSON.
 * Parse le JSON et sauvegarde les articles dans la base de données.
 * 
 * Workflow:
 * 1. Vérifie existence fichier JSON
 * 2. Parse le JSON pour extraire les articles
 * 3. Sauvegarde les articles dans law_articles
 * 4. Prépare status CONSOLIDATED ou FAILED_CONSOLIDATION
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsolidateProcessor implements ItemProcessor<LawDocumentEntity, LawDocumentEntity> {

    private final FileStorageService fileStorageService;
    private final LawArticleService lawArticleService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LawDocumentEntity process(LawDocumentEntity document) {
        String documentId = document.getDocumentId();

        try {
            // Vérifier que le fichier JSON existe
            Path jsonPath = fileStorageService.jsonPath(document.getType(), documentId);

            if (!Files.exists(jsonPath)) {
                String errorMsg = "Fichier JSON introuvable: " + jsonPath;
                log.warn("⚠️ {}", errorMsg);
                document.setStatus(ProcessingStatus.FAILED_CONSOLIDATION);
                document.setErrorMessage(errorMsg);
                return document;
            }

            // Vérifier que le fichier n'est pas vide
            long fileSize = Files.size(jsonPath);
            if (fileSize == 0) {
                String errorMsg = "Fichier JSON vide: " + jsonPath;
                log.warn("⚠️ {}", errorMsg);
                document.setStatus(ProcessingStatus.FAILED_CONSOLIDATION);
                document.setErrorMessage(errorMsg);
                return document;
            }

            // Parser le JSON et extraire les articles
            List<LawArticleEntity> articles = parseArticles(documentId, jsonPath);
            
            if (articles.isEmpty()) {
                log.warn("⚠️ Aucun article extrait pour {}", documentId);
            } else {
                // Supprimer les anciens articles s'ils existent
                lawArticleService.deleteByDocumentId(documentId);
                
                // Debug: logger le premier article
                if (!articles.isEmpty()) {
                    LawArticleEntity first = articles.get(0);
                    log.info("🔍 Premier article: number={}, contentLength={}, hasRawJson={}", 
                            first.getArticleNumber(), 
                            first.getContent() != null ? first.getContent().length() : 0,
                            first.getRawJson() != null);
                }
                
                // Sauvegarder les nouveaux articles
                lawArticleService.saveAll(articles);
                log.info("💾 {} articles sauvegardés pour {}", articles.size(), documentId);
            }

            // Succès: préparer status CONSOLIDATED
            document.setStatus(ProcessingStatus.CONSOLIDATED);
            document.setErrorMessage(null); // Clear previous errors

            log.info("✅ Consolidé: {} ({} bytes, {} articles)", documentId, fileSize, articles.size());
            return document;

        } catch (Exception e) {
            String errorMsg = "Erreur consolidation: " + e.getMessage();
            log.error("❌ {}: {}", documentId, errorMsg);
            document.setStatus(ProcessingStatus.FAILED_CONSOLIDATION);
            document.setErrorMessage(errorMsg);
            return document;
        }
    }

    /**
     * Parse le fichier JSON consolidé et extrait les articles.
     */
    private List<LawArticleEntity> parseArticles(String documentId, Path jsonPath) {
        List<LawArticleEntity> articles = new ArrayList<>();
        
        try {
            String jsonContent = Files.readString(jsonPath);
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            
            // Extraire le tableau "articles" du JSON
            JsonNode articlesNode = rootNode.get("articles");
            if (articlesNode != null && articlesNode.isArray()) {
                for (JsonNode articleNode : articlesNode) {
                    // Debug: afficher le contenu du noeud
                    log.debug("Article node: {}", articleNode.toString());
                    
                    String articleNumber = getTextOrNull(articleNode, "index");
                    String content = getTextOrNull(articleNode, "content");
                    
                    log.debug("Parsed: number={}, contentLength={}", articleNumber, 
                            content != null ? content.length() : 0);
                    
                    LawArticleEntity article = LawArticleEntity.builder()
                            .documentId(documentId)
                            .articleNumber(articleNumber)
                            .content(content)
                            .rawJson(articleNode.toString())
                            .build();
                    
                    articles.add(article);
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Erreur parsing articles pour {}: {}", documentId, e.getMessage());
        }
        
        return articles;
    }

    /**
     * Récupère le texte d'un champ JSON ou null s'il n'existe pas.
     * Gère les nombres et les textes.
     */
    private String getTextOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || field.isMissingNode()) {
            return null;
        }
        return field.asText();  // asText() convertit nombres et textes
    }
}
