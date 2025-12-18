package bj.gouv.sgg;

import bj.gouv.sgg.config.CommonConfiguration;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.service.LawDocumentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration pour LawDocumentService avec H2.
 * Vérifie les opérations CRUD, l'intégrité des données et les contraintes.
 */
@SpringBootTest(classes = CommonConfiguration.class)
@TestPropertySource(locations = "classpath:application-test.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LawDocumentServiceIntegrationTest {
    
    @Autowired
    private LawDocumentService lawDocumentService;
    
    private static final String TEST_DOC_ID = "loi-2025-999";
    
    @AfterAll
    static void cleanup(@Autowired LawDocumentService service) {
        // Supprimer le document de test après tous les tests
        try {
            service.delete(TEST_DOC_ID);
        } catch (Exception e) {
            // Ignore si le document n'existe pas
        }
    }
    
    @Test
    @Order(1)
    void givenSpringContextWhenInitializedThenServiceInjected() {
        assertNotNull(lawDocumentService, "LawDocumentService should be injected");
    }
    
    @Test
    @Order(2)
    void givenNewDocumentWhenSavedThenDocumentPersistedWithCorrectAttributes() {
        LawDocumentEntity record = LawDocumentEntity.create("loi", 2025, "999");
        record.setStatus(ProcessingStatus.FETCHED);
        
        LawDocumentEntity saved = lawDocumentService.save(record);
        
        assertNotNull(saved, "Saved document should not be null");
        assertEquals(TEST_DOC_ID, saved.getDocumentId(), "Document ID should match");
        assertEquals("loi", saved.getType(), "Type should be 'loi'");
        assertEquals(2025, saved.getYear(), "Year should be 2025");
        assertEquals("999", saved.getNumber(), "Number should be 999");
        assertEquals(ProcessingStatus.FETCHED, saved.getStatus(), "Status should be FETCHED");
    }
    
    @Test
    @Order(3)
    void givenPersistedDocumentWhenSearchedByIdThenDocumentFound() {
        Optional<LawDocumentEntity> found = lawDocumentService.findByDocumentId(TEST_DOC_ID);
        
        assertTrue(found.isPresent(), "Document should be found");
        assertEquals(TEST_DOC_ID, found.get().getDocumentId(), "Document ID should match");
    }
    
    @Test
    @Order(4)
    void givenExistingDocumentWhenStatusAndPathUpdatedThenChangesPersistedCorrectly() {
        Optional<LawDocumentEntity> found = lawDocumentService.findByDocumentId(TEST_DOC_ID);
        assertTrue(found.isPresent(), "Document should exist");
        
        LawDocumentEntity record = found.get();
        record.setStatus(ProcessingStatus.DOWNLOADED);
        record.setPdfPath("/data/pdfs/loi/loi-2025-999.pdf");
        
        LawDocumentEntity updated = lawDocumentService.save(record);
        
        assertEquals(ProcessingStatus.DOWNLOADED, updated.getStatus(), "Status should be DOWNLOADED");
        assertEquals("/data/pdfs/loi/loi-2025-999.pdf", updated.getPdfPath(), "PDF path should be set");
    }
    
    @Test
    @Order(5)
    void givenDocumentsWithStatusWhenSearchedByStatusThenMatchingDocumentsReturned() {
        var documents = lawDocumentService.findByStatus(ProcessingStatus.DOWNLOADED);
        
        assertFalse(documents.isEmpty(), "Should find at least 1 document");
        assertTrue(documents.stream()
                .anyMatch(d -> TEST_DOC_ID.equals(d.getDocumentId())),
                "Should find our test document");
    }
    
    @Test
    @Order(6)
    void givenDocumentsWithStatusWhenCountedThenCorrectCountReturned() {
        long count = lawDocumentService.countByStatus(ProcessingStatus.DOWNLOADED);
        
        assertTrue(count >= 1, "Should have at least 1 document with status DOWNLOADED");
    }
    
    @Test
    @Order(7)
    void givenDocumentWithNullTypeWhenSavedThenExceptionThrown() {
        LawDocumentEntity badRecord = LawDocumentEntity.builder()
            .type(null)
            .year(2025)
            .number("888")
            .status(ProcessingStatus.PENDING)
            .build();
        
        assertThrows(Exception.class, () -> lawDocumentService.save(badRecord));
    }
    
    @Test
    @Order(8)
    void givenDuplicateDocumentWhenSavedThenExistingDocumentUpdated() {
        LawDocumentEntity duplicate = LawDocumentEntity.create("loi", 2025, "999");
        
        LawDocumentEntity result = lawDocumentService.save(duplicate);
        
        assertEquals(TEST_DOC_ID, result.getDocumentId(), "Should update existing document");
        
        long count = lawDocumentService.findByTypeAndYear("loi", 2025).stream()
            .filter(d -> d.getNumber().equals("999"))
            .count();
        
        assertEquals(1, count, "Should have only 1 document loi-2025-999");
    }
}
