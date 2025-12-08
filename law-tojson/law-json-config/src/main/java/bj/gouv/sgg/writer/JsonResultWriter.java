package bj.gouv.sgg.writer;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writer Spring Batch pour sauvegarder les résultats JSON d'extraction.
 * 
 * <p><b>Responsabilités</b> :
 * <ul>
 *   <li>Sauvegarder fichier JSON dans {@code data/articles/}</li>
 *   <li>Mettre à jour status document (EXTRACTED ou FAILED)</li>
 *   <li>Persister métadonnées extraction (method, confidence) en base</li>
 * </ul>
 * 
 * <p><b>Format JSON</b> :
 * <pre>{@code
 * {
 *   "_metadata": {
 *     "method": "ollama|groq|ocr",
 *     "confidence": 0.95,
 *     "timestamp": "2025-12-07T12:30:00Z"
 *   },
 *   "type": "loi",
 *   "year": 2024,
 *   "number": 15,
 *   "title": "Loi portant...",
 *   "articles": [...],
 *   "signatories": [...]
 * }
 * }</pre>
 * 
 * <p><b>Idempotence</b> : Le processor a déjà vérifié la confiance, 
 * ce writer écrit uniquement si nécessaire.
 * 
 * @see bj.gouv.sgg.processor.PdfToJsonProcessor
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JsonResultWriter implements ItemWriter<LawDocument> {

    private final LawDocumentRepository lawDocumentRepository;
    private final FileStorageService fileStorageService;
    
    @Override
    public void write(Chunk<? extends LawDocument> chunk) throws Exception {
        for (LawDocument document : chunk) {
            String docId = document.getDocumentId();
            
            try {
                // 1. Sauvegarder JSON (si extrait avec succès)
                if (document.getStatus() == LawDocument.ProcessingStatus.EXTRACTED) {
                    Path jsonPath = fileStorageService.jsonPath(document.getType(), docId);
                    
                    // Récupérer JSON du champ transient (ocrContent réutilisé)
                    String jsonContent = document.getOcrContent();
                    
                    if (jsonContent != null && !jsonContent.isBlank()) {
                        // Créer répertoire parent si nécessaire
                        Files.createDirectories(jsonPath.getParent());
                        
                        // Sauvegarder JSON
                        Files.writeString(jsonPath, jsonContent);
                        
                        log.info("💾 [{}] JSON sauvegardé: {} ({} bytes)", 
                                 docId, jsonPath, jsonContent.length());
                    } else {
                        log.warn("⚠️ [{}] Status EXTRACTED mais JSON vide - Skip sauvegarde", docId);
                    }
                }
                
                // 2. Nettoyer champ transient avant sauvegarde DB
                document.setOcrContent(null);
                
                // 3. ✅ UPSERT: Mettre à jour document en base
                // Note: JPA save() fait automatiquement UPDATE si entity.id existe, INSERT sinon
                // Le document provient déjà du reader donc a un ID, c'est donc un UPDATE
                lawDocumentRepository.save(document);
                
                log.info("✅ [{}] Document mis à jour - Status: {}", 
                         docId, document.getStatus());
                
            } catch (Exception e) {
                log.error("❌ [{}] Erreur sauvegarde résultat: {}", docId, e.getMessage(), e);
                throw e;
            }
        }
    }
}
