package bj.gouv.sgg.entity;

import bj.gouv.sgg.config.DatabaseConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration pour FetchCursorEntity avec MySQL.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FetchCursorEntityTest {
    
    private static EntityManagerFactory emf;
    private EntityManager em;
    
    @BeforeAll
    static void setupDatabase() {
        emf = DatabaseConfig.getInstance().getEntityManagerFactory();
        assertNotNull(emf, "EntityManagerFactory doit être créé");
    }
    
    @AfterAll
    static void teardown() {
        // DatabaseConfig.getInstance().shutdown();
    }
    
    @BeforeEach
    void setup() {
        em = emf.createEntityManager();
        // Nettoyer les cursors de test avant chaque test
        em.getTransaction().begin();
        em.createQuery("DELETE FROM FetchCursorEntity c WHERE c.cursorType IN ('fetch-previous', 'fetch-current')")
          .executeUpdate();
        em.getTransaction().commit();
    }
    
    @AfterEach
    void cleanup() {
        if (em != null && em.isOpen()) {
            em.close();
        }
    }
    
    @Test
    @Order(1)
    void testCreateFetchCursor() {
        // Given
        FetchCursorEntity cursor = FetchCursorEntity.createFetchPrevious("loi", 2023, 1);
        
        // When
        em.getTransaction().begin();
        em.persist(cursor);
        em.getTransaction().commit();
        
        // Then
        assertNotNull(cursor.getId(), "L'ID doit être généré");
        assertEquals("fetch-previous", cursor.getCursorType());
        assertEquals("loi", cursor.getDocumentType());
        assertEquals(2023, cursor.getCurrentYear());
        assertEquals(1, cursor.getCurrentNumber());
        assertNotNull(cursor.getUpdatedAt());
    }
    
    @Test
    @Order(2)
    void testFindCursorByType() {
        // Given
        FetchCursorEntity cursor1 = FetchCursorEntity.createFetchPrevious("loi", 2022, 100);
        FetchCursorEntity cursor2 = FetchCursorEntity.createFetchCurrent("decret", 2025, 500);
        
        em.getTransaction().begin();
        em.persist(cursor1);
        em.persist(cursor2);
        em.getTransaction().commit();
        em.clear();
        
        // When
        TypedQuery<FetchCursorEntity> query = em.createQuery(
            "SELECT c FROM FetchCursorEntity c WHERE c.cursorType = :type", 
            FetchCursorEntity.class
        );
        query.setParameter("type", "fetch-current");
        List<FetchCursorEntity> results = query.getResultList();
        
        // Then
        assertFalse(results.isEmpty(), "Doit trouver au moins un cursor");
        FetchCursorEntity found = results.stream()
            .filter(c -> c.getDocumentType().equals("decret"))
            .findFirst()
            .orElse(null);
        
        assertNotNull(found);
        assertEquals("fetch-current", found.getCursorType());
        assertEquals("decret", found.getDocumentType());
        assertEquals(2025, found.getCurrentYear());
        assertEquals(500, found.getCurrentNumber());
    }
    
    @Test
    @Order(3)
    void testUpdateCursor() {
        // Given - Créer un cursor
        FetchCursorEntity cursor = FetchCursorEntity.createFetchPrevious("loi", 2020, 1);
        em.getTransaction().begin();
        em.persist(cursor);
        em.getTransaction().commit();
        Long cursorId = cursor.getId();
        em.clear();
        
        // When - Le mettre à jour
        em.getTransaction().begin();
        FetchCursorEntity toUpdate = em.find(FetchCursorEntity.class, cursorId);
        LocalDateTime beforeUpdate = toUpdate.getUpdatedAt();
        
        toUpdate.setCurrentYear(2021);
        toUpdate.setCurrentNumber(50);
        em.getTransaction().commit();
        em.clear();
        
        // Then - Vérifier les changements
        FetchCursorEntity updated = em.find(FetchCursorEntity.class, cursorId);
        assertEquals(2021, updated.getCurrentYear());
        assertEquals(50, updated.getCurrentNumber());
        assertTrue(updated.getUpdatedAt().isAfter(beforeUpdate) || 
                   updated.getUpdatedAt().isEqual(beforeUpdate));
    }
    
    @Test
    @Order(4)
    void testPrePersistSetsUpdatedAt() {
        // Given
        FetchCursorEntity cursor = new FetchCursorEntity();
        cursor.setCursorType("fetch-previous");
        cursor.setDocumentType("decret");
        cursor.setCurrentYear(2019);
        cursor.setCurrentNumber(1);
        // Pas de updatedAt défini
        
        // When
        em.getTransaction().begin();
        em.persist(cursor);
        em.getTransaction().commit();
        
        // Then
        assertNotNull(cursor.getUpdatedAt(), "updatedAt doit être défini par @PrePersist");
    }
    
    @Test
    @Order(5)
    void testFindCursorByTypeAndDocumentType() {
        // Given
        FetchCursorEntity cursor = FetchCursorEntity.createFetchPrevious("loi", 2018, 200);
        em.getTransaction().begin();
        em.persist(cursor);
        em.getTransaction().commit();
        em.clear();
        
        // When
        TypedQuery<FetchCursorEntity> query = em.createQuery(
            "SELECT c FROM FetchCursorEntity c WHERE c.cursorType = :cursorType AND c.documentType = :docType", 
            FetchCursorEntity.class
        );
        query.setParameter("cursorType", "fetch-previous");
        query.setParameter("docType", "loi");
        List<FetchCursorEntity> results = query.getResultList();
        
        // Then
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(c -> 
            c.getCurrentYear() == 2018 && c.getCurrentNumber() == 200
        ));
    }
}
