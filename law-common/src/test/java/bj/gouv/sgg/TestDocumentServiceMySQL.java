package bj.gouv.sgg;

import bj.gouv.sgg.config.DatabaseConfig;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.service.LawDocumentService;

/**
 * Test simple d'insertion de documents dans MySQL.
 */
public class TestDocumentServiceMySQL {
    
    public static void main(String[] args) {
        System.out.println("🚀 Test DocumentService avec MySQL");
        System.out.println("===================================\n");
        
        LawDocumentService service = new LawDocumentService();
        
        try {
            // Créer et sauvegarder 3 documents
            for (int i = 100; i <= 102; i++) {
                LawDocumentEntity doc = LawDocumentEntity.create("loi", 2024, i);
                doc.setStatus(ProcessingStatus.FETCHED);
                // doc.setUrl("https://sgg.gouv.bj/doc/loi-2024-" + i + ".pdf");
                // doc.setTitle("Loi N°" + i + " de 2024");
                
                service.save(doc);
                System.out.println("✅ Sauvegardé: loi-2024-" + i);
            }
            
            // Compter
            long total = service.countByStatus(ProcessingStatus.FETCHED);
            System.out.println("\n📊 Total documents FETCHED: " + total);
            
            // Lire un document
            var doc = service.findByDocumentId("loi-2024-100");
            if (doc.isPresent()) {
                System.out.println("\n🔍 Document trouvé:");
                System.out.println("   ID: " + doc.get().getDocumentId());
                System.out.println("   Status: " + doc.get().getStatus());
                // System.out.println("   Title: " + doc.get().getTitle());
                // System.out.println("   URL: " + doc.get().getUrl());
            }
            
            // Vérifier qu'aucun n'a type null
            var allDocs = service.findByStatus(ProcessingStatus.FETCHED);
            long nullCount = allDocs.stream()
                .filter(d -> d.getType() == null || d.getType().isEmpty())
                .count();
            
            System.out.println("\n✅ Tous les documents ont un type valide: " + (nullCount == 0));
            System.out.println("✅ Test réussi - MySQL fonctionne !");
            
        } catch (Exception e) {
            System.err.println("\n❌ Erreur: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            service.close();
            DatabaseConfig.getInstance().shutdown();
            System.out.println("\n🛑 Connexion fermée");
        }
    }
}
