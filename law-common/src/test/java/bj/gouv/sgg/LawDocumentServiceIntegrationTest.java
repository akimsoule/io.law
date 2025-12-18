package bj.gouv.sgg;

import bj.gouv.sgg.config.DatabaseConfig;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.service.LawDocumentService;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration pour LawDocumentService avec MySQL.
 * Vérifie les opérations CRUD, l'intégrité des données et les contraintes.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LawDocumentServiceIntegrationTest {
    
    private static LawDocumentService lawDocumentService;
    private static final String TEST_DOC_ID = "loi-2025-999";
    
    @BeforeAll
    static void setup() {
        System.out.println("🔧 Initializing MySQL connection...");
        lawDocumentService = new LawDocumentService();
    }
    
    @AfterAll
    static void cleanup() {
        System.out.println("🧹 Cleaning up...");
        
        // Supprimer le document de test
        try {
            lawDocumentService.delete(TEST_DOC_ID);
            System.out.println("✅ Test document deleted");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to delete test document: " + e.getMessage());
        }
        
        lawDocumentService.close();
        DatabaseConfig.getInstance().shutdown();
    }
    
    @Test
    @Order(1)
    void givenDatabaseConfig_whenInitialized_thenConnectionEstablished() {
        System.out.println("\n📊 Test 1: Database Connection");
        
        assertNotNull(DatabaseConfig.getInstance(), "DatabaseConfig should be initialized");
        assertNotNull(DatabaseConfig.getInstance().getEntityManagerFactory(), 
                     "EntityManagerFactory should be created");
        
        System.out.println("✅ Database connection OK");
    }
    
    @Test
    @Order(2)
    void givenNewDocument_whenSaved_thenDocumentPersistedWithCorrectAttributes() {
        System.out.println("\n📊 Test 2: Insert Document");
        
        // Créer un document de test
        LawDocumentEntity record = LawDocumentEntity.create("loi", 2025, 999);
        // record.setUrl("https://sgg.gouv.bj/doc/loi-2025-999.pdf");
        // record.setTitle("Loi de test MySQL");
        record.setStatus(ProcessingStatus.FETCHED);
        
        // Sauvegarder
        LawDocumentEntity saved = lawDocumentService.save(record);
        
        assertNotNull(saved, "Saved document should not be null");
        assertEquals(TEST_DOC_ID, saved.getDocumentId(), "Document ID should match");
        assertEquals("loi", saved.getType(), "Type should be 'loi'");
        assertEquals(2025, saved.getYear(), "Year should be 2025");
        assertEquals(999, saved.getNumber(), "Number should be 999");
        assertEquals(ProcessingStatus.FETCHED, saved.getStatus(), "Status should be FETCHED");
        
        System.out.println("✅ Document inserted: " + TEST_DOC_ID);
        // System.out.println("   Title: " + saved.getTitle());
        // System.out.println("   URL: " + saved.getUrl());
    }
    
    @Test
    @Order(3)
    void givenPersistedDocument_whenSearchedById_thenDocumentFound() {
        System.out.println("\n📊 Test 3: Find Document");
        
        // Chercher le document
        Optional<LawDocumentEntity> found = lawDocumentService.findByDocumentId(TEST_DOC_ID);
        
        assertTrue(found.isPresent(), "Document should be found");
        assertEquals(TEST_DOC_ID, found.get().getDocumentId(), "Document ID should match");
        // assertEquals("Loi de test MySQL", found.get().getTitle(), "Title should match");
        
        System.out.println("✅ Document found: " + found.get().getDocumentId());
    }
    
    @Test
    @Order(4)
    void givenExistingDocument_whenStatusAndPathUpdated_thenChangesPersistedCorrectly() {
        System.out.println("\n📊 Test 4: Update Document");
        
        // Chercher et mettre à jour
        Optional<LawDocumentEntity> found = lawDocumentService.findByDocumentId(TEST_DOC_ID);
        assertTrue(found.isPresent(), "Document should exist");
        
        LawDocumentEntity record = found.get();
        record.setStatus(ProcessingStatus.DOWNLOADED);
        record.setPdfPath("/data/pdfs/loi/loi-2025-999.pdf");
        
        LawDocumentEntity updated = lawDocumentService.save(record);
        
        assertEquals(ProcessingStatus.DOWNLOADED, updated.getStatus(), "Status should be DOWNLOADED");
        assertEquals("/data/pdfs/loi/loi-2025-999.pdf", updated.getPdfPath(), "PDF path should be set");
        
        System.out.println("✅ Document updated: " + TEST_DOC_ID);
        System.out.println("   New status: " + updated.getStatus());
        System.out.println("   PDF path: " + updated.getPdfPath());
    }
    
    @Test
    @Order(5)
    void givenDocumentsWithStatus_whenSearchedByStatus_thenMatchingDocumentsReturned() {
        System.out.println("\n📊 Test 5: Find by Status");
        
        var documents = lawDocumentService.findByStatus(ProcessingStatus.DOWNLOADED);
        
        assertFalse(documents.isEmpty(), "Should find at least 1 document");
        assertTrue(documents.stream()
                .anyMatch(d -> TEST_DOC_ID.equals(d.getDocumentId())),
                "Should find our test document");
        
        System.out.println("✅ Found " + documents.size() + " document(s) with status DOWNLOADED");
    }
    
    @Test
    @Order(6)
    void givenDocumentsWithStatus_whenCounted_thenCorrectCountReturned() {
        System.out.println("\n📊 Test 6: Count by Status");
        
        long count = lawDocumentService.countByStatus(ProcessingStatus.DOWNLOADED);
        
        assertTrue(count >= 1, "Should have at least 1 document with status DOWNLOADED");
        
        System.out.println("✅ Total DOWNLOADED documents: " + count);
    }
    
    @Test
    @Order(7)
    void givenDocumentWithNullType_whenSaved_thenExceptionThrown() {
        System.out.println("\n📊 Test 7: Prevent NULL type (data integrity)");
        
        // Créer un document avec type null
        LawDocumentEntity badRecord = LawDocumentEntity.builder()
            .type(null)  // ❌ This should be rejected by DB
            .year(2025)
            .number("888")
            .status(ProcessingStatus.PENDING)
            .build();
        
        // Tenter de sauvegarder
        Exception exception = assertThrows(Exception.class, () -> {
            lawDocumentService.save(badRecord);
        });
        
        System.out.println("✅ NULL type rejected (as expected)");
        System.out.println("   Error: " + exception.getClass().getSimpleName());
    }
    
    @Test
    @Order(8)
    void givenDuplicateDocument_whenSaved_thenExistingDocumentUpdated() {
        System.out.println("\n📊 Test 8: Prevent Duplicates (unique constraint)");
        
        // Créer un document avec même type/year/number
        LawDocumentEntity duplicate = LawDocumentEntity.create("loi", 2025, 999);
        // duplicate.setUrl("https://different-url.pdf");
        
        // La sauvegarde devrait mettre à jour, pas insérer
        LawDocumentEntity result = lawDocumentService.save(duplicate);
        
        assertEquals(TEST_DOC_ID, result.getDocumentId(), "Should update existing document");
        
        // Vérifier qu'il n'y a qu'un seul document loi-2025-999
        long count = lawDocumentService.findByTypeAndYear("loi", 2025).stream()
            .filter(d -> d.getNumber().equals("999"))
            .count();
        
        assertEquals(1, count, "Should have only 1 document loi-2025-999");
        
        System.out.println("✅ Duplicate prevented (update instead of insert)");
    }
}
