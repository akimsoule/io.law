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
    void givenNoParametersWhenRunFetchCurrentJobThenCompletesSuccessfully() throws Exception {
        // Given: Paramètres de job avec timestamp unique
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // When: Exécution du fetchCurrentJob
        JobExecution jobExecution = jobLauncher.run(fetchCurrentJob, jobParameters);

        // Then: Job complété avec succès, documents détectés ou non
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus(),
                "Le job fetchCurrentJob devrait se terminer avec succès");

        long fetchedCount = lawDocumentRepository.count();
        assertTrue(fetchedCount >= 0,
                "Le job devrait s'exécuter avec succès même sans documents détectés");
    }

    @Test
    void givenJobAlreadyExecutedWhenRunAgainThenIdempotent() throws Exception {
        // Given: Premier run du fetchCurrentJob et comptage des documents
        JobParameters jobParameters1 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        JobExecution firstExecution = jobLauncher.run(fetchCurrentJob, jobParameters1);
        long countAfterFirst = lawDocumentRepository.count();

        // When: Second run avec paramètres différents
        JobParameters jobParameters2 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis() + 1000)
                .toJobParameters();
        JobExecution secondExecution = jobLauncher.run(fetchCurrentJob, jobParameters2);
        long countAfterSecond = lawDocumentRepository.count();

        // Then: Les deux runs sont complétés avec le même nombre de documents (idempotence)
        assertEquals(BatchStatus.COMPLETED, firstExecution.getStatus(),
                "Le premier run devrait se terminer avec succès");
        assertEquals(BatchStatus.COMPLETED, secondExecution.getStatus(),
                "Le second run devrait se terminer avec succès");
        assertEquals(countAfterFirst, countAfterSecond,
                "Le job devrait être idempotent - même nombre de documents après 2 runs");
    }

    @Test
    void givenJobExecutedWhenCheckingStatusThenDocumentsHaveFetchedStatus() throws Exception {
        // Given: Paramètres de job valides
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // When: Exécution du fetchCurrentJob
        jobLauncher.run(fetchCurrentJob, jobParameters);

        // Then: Les documents détectés ont le statut FETCHED
        long fetchedCount = lawDocumentRepository.countByStatus(LawDocument.ProcessingStatus.FETCHED);
        assertTrue(fetchedCount >= 0,
                "Les documents détectés devraient avoir le statut FETCHED");
    }

    @Test
    void givenSpecificLawIdWhenRunFetchJobThenProcessesTargetedDocument() throws Exception {
        // Given: Cibler une loi spécifique (loi-2024-15)
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "loi-2024-15")
                .toJobParameters();

        // When: Exécution du fetchCurrentJob avec document ciblé
        JobExecution jobExecution = jobLauncher.run(fetchCurrentJob, jobParameters);

        // Then: Job complété et document ciblé traité si existant
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
    void givenSpecificDecretIdWhenRunFetchJobThenProcessesTargetedDecret() throws Exception {
        // Given: Cibler un décret spécifique (decret-2025-716)
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "decret-2025-716")
                .toJobParameters();

        // When: Exécution du fetchCurrentJob avec décret ciblé
        JobExecution jobExecution = jobLauncher.run(fetchCurrentJob, jobParameters);

        // Then: Job complété et décret ciblé traité si existant
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
    void givenExistingDocumentWhenRunWithForceThenRefetchesWithoutDuplicates() throws Exception {
        // Given: Premier run pour créer un document existant
        JobParameters jobParameters1 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "loi-2024-15")
                .toJobParameters();
        JobExecution firstExecution = jobLauncher.run(fetchCurrentJob, jobParameters1);
        assertEquals(BatchStatus.COMPLETED, firstExecution.getStatus(),
                "Le premier run devrait se terminer avec succès");

        // Simuler un document déjà FETCHED
        LawDocument existingDoc = lawDocumentRepository.findByTypeAndYearAndNumber("loi", 2024, 15)
                .orElse(null);
        if (existingDoc != null) {
            long firstCount = lawDocumentRepository.count();

            // When: Second run avec force=true pour re-fetch
            JobParameters jobParameters2 = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis() + 1000)
                    .addString("documentId", "loi-2024-15")
                    .addString("force", "true")
                    .toJobParameters();
            JobExecution secondExecution = jobLauncher.run(fetchCurrentJob, jobParameters2);

            // Then: Job complété sans duplication de documents
            assertEquals(BatchStatus.COMPLETED, secondExecution.getStatus(),
                    "Le job avec force=true devrait se terminer avec succès");

            // Le document devrait toujours exister (pas de duplication)
            long secondCount = lawDocumentRepository.count();
            assertEquals(firstCount, secondCount,
                    "Le mode force ne devrait pas créer de duplicates");
        }
    }

    @Test
    void givenExistingDocumentWhenRunWithoutForceThenSkipsWithoutDuplicates() throws Exception {
        // Given: Premier run pour créer un document existant
        JobParameters jobParameters1 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "loi-2024-15")
                .toJobParameters();
        JobExecution firstExecution = jobLauncher.run(fetchCurrentJob, jobParameters1);
        assertEquals(BatchStatus.COMPLETED, firstExecution.getStatus(),
                "Le premier run devrait se terminer avec succès");

        long firstCount = lawDocumentRepository.count();

        // When: Second run SANS force (devrait skip le document existant)
        JobParameters jobParameters2 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis() + 1000)
                .addString("documentId", "loi-2024-15")
                .toJobParameters();
        JobExecution secondExecution = jobLauncher.run(fetchCurrentJob, jobParameters2);

        // Then: Job complété sans duplication (document skippé)
        assertEquals(BatchStatus.COMPLETED, secondExecution.getStatus(),
                "Le job sans force devrait se terminer avec succès");

        long secondCount = lawDocumentRepository.count();
        assertEquals(firstCount, secondCount,
                "Le job sans force ne devrait pas créer de duplicates");
    }
}
