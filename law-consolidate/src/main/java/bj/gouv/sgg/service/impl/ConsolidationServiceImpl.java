package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.exception.ConsolidationException;
import bj.gouv.sgg.model.ConsolidationResult;
import bj.gouv.sgg.model.DocumentRecord;
import bj.gouv.sgg.model.ProcessingStatus;
import bj.gouv.sgg.service.ConsolidationService;
import bj.gouv.sgg.service.DocumentService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Implémentation du service de consolidation.
 */
@Slf4j
public class ConsolidationServiceImpl implements ConsolidationService {
    
    private static ConsolidationServiceImpl instance;
    
    private final AppConfig config;
    private final Path jsonDir;
    private final DocumentService documentService;
    
    private ConsolidationServiceImpl() {
        this.config = AppConfig.get();
        this.jsonDir = config.getStoragePath().resolve("articles");
        this.documentService = new DocumentService();
    }
    
    public static synchronized ConsolidationServiceImpl getInstance() {
        if (instance == null) {
            instance = new ConsolidationServiceImpl();
        }
        return instance;
    }
    
    @Override
    public void runDocument(String documentId) {
        log.info("🗄️ consolidate: documentId={}", documentId);
        
        try {
            // Parse documentId
            String[] parts = documentId.split("-");
            if (parts.length != 3) {
                String errorMsg = "Format invalide: " + documentId;
                log.warn("⚠️ {}", errorMsg);
                return;
            }
            
            String type = parts[0];
            int year = Integer.parseInt(parts[1]);
            int number = Integer.parseInt(parts[2]);
            
            Path jsonFile = jsonDir.resolve(type).resolve(documentId + ".json");
            
            if (!Files.exists(jsonFile)) {
                String errorMsg = "JSON file not found: " + documentId;
                log.warn("⚠️ JSON absent: {}", documentId);
                return;
            }
            
            // Vérifier si déjà consolidé
            Optional<DocumentRecord> docOpt = documentService.findByDocumentId(documentId);
            
            if (docOpt.isEmpty()) {
                String errorMsg = "Document non trouvé dans DB: " + documentId;
                log.warn("⚠️ {}", errorMsg);
                return;
            }
            
            DocumentRecord doc = docOpt.get();
            
            if (doc.getStatus() == ProcessingStatus.CONSOLIDATED) {
                log.debug("⏭️ Déjà consolidé (status): {}", documentId);
                return;
            }
            
            if (doc.getStatus() != ProcessingStatus.EXTRACTED) {
                String errorMsg = "Status incorrect: " + doc.getStatus() + " (attendu: EXTRACTED)";
                log.warn("⚠️ {}", errorMsg);
                return;
            }
            
            // Consolider
            doc.setStatus(ProcessingStatus.CONSOLIDATED);
            documentService.save(doc);
            
            log.info("✅ Consolidé: {}", documentId);
            
        } catch (NumberFormatException e) {
            String errorMsg = "Format numérique invalide dans documentId: " + documentId;
            log.warn("⚠️ {}", errorMsg);
        } catch (Exception e) {
            log.error("❌ Erreur consolidation {}: {}", documentId, e.getMessage());
        }
    }
    
    @Override
    public void runType(String type) {
        log.info("🗄️ ConsolidateJob: type={}", type);
        
        try {
            Path typeJsonDir = jsonDir.resolve(type);
            
            if (!Files.exists(typeJsonDir)) {
                log.warn("⚠️ Répertoire JSON absent: {}", typeJsonDir);
                return;
            }
            
            List<Path> jsonFiles;
            try (Stream<Path> stream = Files.list(typeJsonDir)) {
                jsonFiles = stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .toList();
            }
            
            log.info("📄 {} fichiers JSON trouvés", jsonFiles.size());
            
            if (jsonFiles.isEmpty()) {
                log.warn("⚠️ Aucun fichier JSON à consolider");
                return;
            }
            
            // Traiter chaque fichier JSON
            for (Path jsonFile : jsonFiles) {
                String fileName = jsonFile.getFileName().toString();
                String documentId = fileName.replace(".json", "");
                runDocument(documentId);
            }
            
            log.info("✅ ConsolidateJob terminé");
            
        } catch (IOException e) {
            log.error("❌ Erreur ConsolidateJob: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Erreur inattendue ConsolidateJob: {}", e.getMessage());
        }
    }
}
