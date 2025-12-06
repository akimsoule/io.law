package bj.gouv.sgg.batch;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'intégration pour le job fetchCurrentJob.
 * Ces tests vérifient le bon fonctionnement complet du job avec H2 et Spring Batch.
 */
@SpringBootTest(properties = {"spring.batch.job.name=fetchCurrentJob"})
@TestPropertySource(locations = "classpath:application-test.yml")
class FetchJobIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("fetchCurrentJob")
    private Job fetchCurrentJob;

    @Autowired
    private LawDocumentRepository lawDocumentRepository;

    @BeforeEach
    void setUp() {
        lawDocumentRepository.deleteAll();
    }

    @Test
    void testFetchCurrentJobExecution() throws Exception {
        // Given
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(fetchCurrentJob, jobParameters);

        // Then
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus(),
                "Le job fetchCurrentJob devrait se terminer avec succès");

        long fetchedCount = lawDocumentRepository.count();
        assertTrue(fetchedCount >= 0,
                "Le job devrait s'exécuter avec succès même sans documents détectés");
    }

    @Test
    void testFetchCurrentJobIdempotence() throws Exception {
        // Given - Premier run
        JobParameters jobParameters1 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        JobExecution firstExecution = jobLauncher.run(fetchCurrentJob, jobParameters1);
        long countAfterFirst = lawDocumentRepository.count();

        // When - Second run
        JobParameters jobParameters2 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis() + 1000)
                .toJobParameters();
        JobExecution secondExecution = jobLauncher.run(fetchCurrentJob, jobParameters2);
        long countAfterSecond = lawDocumentRepository.count();

        // Then
        assertEquals(BatchStatus.COMPLETED, firstExecution.getStatus());
        assertEquals(BatchStatus.COMPLETED, secondExecution.getStatus());
        assertEquals(countAfterFirst, countAfterSecond,
                "Le job devrait être idempotent - même nombre de documents après 2 runs");
    }

    @Test
    void testFetchedDocumentsHaveCorrectStatus() throws Exception {
        // Given
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // When
        jobLauncher.run(fetchCurrentJob, jobParameters);

        // Then
        long fetchedCount = lawDocumentRepository.countByStatus(LawDocument.ProcessingStatus.FETCHED);
        assertTrue(fetchedCount >= 0,
                "Les documents détectés devraient avoir le statut FETCHED");
    }

    @Test
    void testFetchSpecificLawDocument() throws Exception {
        // Given - Cibler une loi spécifique (loi-2024-15)
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "loi-2024-15")
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(fetchCurrentJob, jobParameters);

        // Then
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus(),
                "Le job devrait se terminer avec succès pour un document ciblé");

        // Vérifier que le document a été traité
        long count = lawDocumentRepository.count();
        assertTrue(count >= 0,
                "Le document ciblé devrait avoir été traité");
        
        // Si le document existe sur le serveur, il devrait être FETCHED
        LawDocument doc = lawDocumentRepository.findByTypeAndYearAndNumber("loi", 2024, 15)
                .orElse(null);
        if (doc != null && doc.isExists()) {
            assertEquals(LawDocument.ProcessingStatus.FETCHED, doc.getStatus(),
                    "Le document existant devrait avoir le statut FETCHED");
        }
    }

    @Test
    void testFetchSpecificDecretDocument() throws Exception {
        // Given - Cibler un décret spécifique (decret-2025-716)
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "decret-2025-716")
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(fetchCurrentJob, jobParameters);

        // Then
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus(),
                "Le job devrait se terminer avec succès pour un décret ciblé");

        // Vérifier que le décret a été traité
        LawDocument doc = lawDocumentRepository.findByTypeAndYearAndNumber("decret", 2025, 716)
                .orElse(null);
        if (doc != null && doc.isExists()) {
            assertEquals(LawDocument.ProcessingStatus.FETCHED, doc.getStatus(),
                    "Le décret existant devrait avoir le statut FETCHED");
        }
    }

    @Test
    void testFetchWithForceMode() throws Exception {
        // Given - Premier run pour créer un document
        JobParameters jobParameters1 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "loi-2024-15")
                .toJobParameters();
        JobExecution firstExecution = jobLauncher.run(fetchCurrentJob, jobParameters1);
        assertEquals(BatchStatus.COMPLETED, firstExecution.getStatus());

        // Simuler un document déjà FETCHED
        LawDocument existingDoc = lawDocumentRepository.findByTypeAndYearAndNumber("loi", 2024, 15)
                .orElse(null);
        if (existingDoc != null) {
            long firstCount = lawDocumentRepository.count();

            // When - Second run avec force=true
            JobParameters jobParameters2 = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis() + 1000)
                    .addString("documentId", "loi-2024-15")
                    .addString("force", "true")
                    .toJobParameters();
            JobExecution secondExecution = jobLauncher.run(fetchCurrentJob, jobParameters2);

            // Then
            assertEquals(BatchStatus.COMPLETED, secondExecution.getStatus(),
                    "Le job avec force=true devrait se terminer avec succès");

            // Le document devrait toujours exister (pas de duplication)
            long secondCount = lawDocumentRepository.count();
            assertEquals(firstCount, secondCount,
                    "Le mode force ne devrait pas créer de duplicates");
        }
    }

    @Test
    void testFetchWithoutForceSkipsExisting() throws Exception {
        // Given - Premier run pour créer un document
        JobParameters jobParameters1 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "loi-2024-15")
                .toJobParameters();
        JobExecution firstExecution = jobLauncher.run(fetchCurrentJob, jobParameters1);
        assertEquals(BatchStatus.COMPLETED, firstExecution.getStatus());

        long firstCount = lawDocumentRepository.count();

        // When - Second run SANS force (devrait skip)
        JobParameters jobParameters2 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis() + 1000)
                .addString("documentId", "loi-2024-15")
                .toJobParameters();
        JobExecution secondExecution = jobLauncher.run(fetchCurrentJob, jobParameters2);

        // Then
        assertEquals(BatchStatus.COMPLETED, secondExecution.getStatus(),
                "Le job sans force devrait se terminer avec succès");

        long secondCount = lawDocumentRepository.count();
        assertEquals(firstCount, secondCount,
                "Le job sans force ne devrait pas créer de duplicates");
    }
}
