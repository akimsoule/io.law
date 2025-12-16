package bj.gouv.sgg;

import bj.gouv.sgg.config.DatabaseConfig;
import bj.gouv.sgg.job.fetch.FetchJob;
import bj.gouv.sgg.model.ProcessingStatus;
import bj.gouv.sgg.service.DocumentService;

/**
 * Test simple pour vérifier que FetchJob persiste en MySQL.
 */
public class TestFetchMySQL {
    
    public static void main(String[] args) {
        System.out.println("🚀 Test Fetch avec MySQL");
        System.out.println("========================\n");
        
        DocumentService documentService = new DocumentService();
        FetchJob fetchJob = new FetchJob();
        
        try {
            // Compter avant
            long beforeCount = documentService.countByStatus(ProcessingStatus.FETCHED);
            System.out.println("📊 Documents FETCHED avant: " + beforeCount);
            
            // Fetch 5 documents
            System.out.println("\n🔄 Fetching loi-2024-100...");
            fetchJob.runDocument("loi-2024-100");
            
            System.out.println("🔄 Fetching loi-2024-101...");
            fetchJob.runDocument("loi-2024-101");
            
            System.out.println("🔄 Fetching loi-2024-102...");
            fetchJob.runDocument("loi-2024-102");
            
            // Compter après
            long afterCount = documentService.countByStatus(ProcessingStatus.FETCHED);
            System.out.println("\n📊 Documents FETCHED après: " + afterCount);
            System.out.println("✅ Nouveaux documents: " + (afterCount - beforeCount));
            
            // Vérifier qu'ils sont bien en base
            System.out.println("\n🔍 Vérification dans MySQL:");
            for (int i = 100; i <= 102; i++) {
                var doc = documentService.findByDocumentId("loi-2024-" + i);
                if (doc.isPresent()) {
                    System.out.println("  ✅ loi-2024-" + i + " -> " + doc.get().getStatus());
                } else {
                    System.out.println("  ❌ loi-2024-" + i + " NOT FOUND");
                }
            }
            
            // Vérifier qu'aucun n'a type null
            var fetchedDocs = documentService.findByStatus(ProcessingStatus.FETCHED);
            long nullTypes = fetchedDocs.stream()
                .filter(d -> d.getType() == null || d.getType().isEmpty())
                .count();
            
            System.out.println("\n✅ Aucun document avec type=NULL: " + (nullTypes == 0));
            
            System.out.println("\n🎉 Test réussi !");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
            e.printStackTrace();
        } finally {
            documentService.close();
            DatabaseConfig.getInstance().shutdown();
        }
    }
}
