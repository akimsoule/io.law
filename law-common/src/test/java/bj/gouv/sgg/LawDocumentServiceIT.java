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
 * Tests d'intégration du LawDocumentService.
 */
@SpringBootTest(classes = CommonConfiguration.class)
@TestPropertySource(locations = "classpath:application-test.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LawDocumentServiceIT {
    
    @Autowired
    private LawDocumentService service;
    
    private static final String TEST_DOC_PREFIX = "loi-2024-";
    
    @AfterAll
    static void cleanup(@Autowired LawDocumentService service) {
        for (int i = 100; i <= 102; i++) {
            try {
                service.delete(TEST_DOC_PREFIX + i);
            } catch (Exception e) {
                // Ignore if document doesn't exist
            }
        }
    }
    
    @Test
    @Order(1)
    void testInsertMultipleDocuments() {
        for (int i = 100; i <= 102; i++) {
            LawDocumentEntity doc = LawDocumentEntity.create("loi", 2024, String.valueOf(i));
            doc.setStatus(ProcessingStatus.FETCHED);
            
            LawDocumentEntity saved = service.save(doc);
            
            assertNotNull(saved);
            assertEquals(TEST_DOC_PREFIX + i, saved.getDocumentId());
        }
    }
    
    @Test
    @Order(2)
    void testCountByStatus() {
        long total = service.countByStatus(ProcessingStatus.FETCHED);
        
        assertTrue(total >= 3, "Should have at least 3 FETCHED documents");
    }
    
    @Test
    @Order(3)
    void testFindByDocumentId() {
        Optional<LawDocumentEntity> doc = service.findByDocumentId(TEST_DOC_PREFIX + "100");
        
        assertTrue(doc.isPresent(), "Document should be found");
        assertEquals(TEST_DOC_PREFIX + "100", doc.get().getDocumentId());
        assertEquals(ProcessingStatus.FETCHED, doc.get().getStatus());
    }
    
    @Test
    @Order(4)
    void testAllDocumentsHaveValidType() {
        var allDocs = service.findByStatus(ProcessingStatus.FETCHED);
        long nullCount = allDocs.stream()
            .filter(d -> d.getType() == null || d.getType().isEmpty())
            .count();
        
        assertEquals(0, nullCount, "All documents should have a valid type");
    }
}
