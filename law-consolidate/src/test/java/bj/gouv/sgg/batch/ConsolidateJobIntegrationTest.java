package bj.gouv.sgg.batch;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration pour ConsolidateJob.
 * Utilise de vrais fichiers JSON et une base H2.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConsolidateJobIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job consolidateJob;

    @Autowired
    private LawDocumentRepository repository;

    @Autowired
    private FileStorageService fileStorageService;

    private Path jsonDir;

    @BeforeEach
    void setUp() throws IOException {
        // Nettoyer la base
        repository.deleteAll();

        // Créer répertoire JSON de test
        jsonDir = Path.of("/tmp/law-test/articles/loi");
        Files.createDirectories(jsonDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Nettoyer les fichiers de test
        if (Files.exists(jsonDir)) {
            Files.walk(jsonDir)
                    .sorted((a, b) -> -a.compareTo(b)) // Ordre inverse pour supprimer enfants avant parents
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    /**
     * Test 1: Consolidation d'un document EXTRACTED avec JSON existant.
     */
    @Test
    void shouldConsolidateExtractedDocumentWithJson() throws Exception {
        // GIVEN: Document EXTRACTED avec fichier JSON
        LawDocumentEntity doc = new LawDocumentEntity();
        doc.setType("loi");
        doc.setYear(2018);
        doc.setNumber("027");
        doc.setDocumentId("loi-2018-027");
        doc.setStatus(ProcessingStatus.EXTRACTED);
        doc.setJsonPath("/tmp/law-test/articles/loi/loi-2018-027.json");
        repository.save(doc);

        // Créer fichier JSON réel
        Path jsonFile = jsonDir.resolve("loi-2018-027.json");
        String jsonContent = """
                {
                  "articles": [
                    {
                      "numero": "1",
                      "contenu": "Article 1er test"
                    }
                  ],
                  "metadata": {
                    "promulgationDate": "2018-07-01"
                  },
                  "confidence": 0.8,
                  "method": "OCR"
                }
                """;
        Files.writeString(jsonFile, jsonContent);

        // WHEN: Lancement du job
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("type", "loi")
                .addString("documentId", "ALL")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(consolidateJob, jobParameters);

        // THEN: Job réussi
        assertEquals("COMPLETED", execution.getExitStatus().getExitCode());

        // Vérifier status CONSOLIDATED
        Optional<LawDocumentEntity> updated = repository.findByTypeAndYearAndNumber("loi", 2018, "027");
        assertTrue(updated.isPresent());
        assertEquals(ProcessingStatus.CONSOLIDATED, updated.get().getStatus());
        assertNull(updated.get().getErrorMessage());
    }

    /**
     * Test 2: Skip document déjà CONSOLIDATED (idempotence).
     */
    @Test
    void shouldSkipAlreadyConsolidatedDocument() throws Exception {
        // GIVEN: Document déjà CONSOLIDATED
        LawDocumentEntity doc = new LawDocumentEntity();
        doc.setType("loi");
        doc.setYear(2019);
        doc.setNumber("045");
        doc.setDocumentId("loi-2019-045");
        doc.setStatus(ProcessingStatus.CONSOLIDATED);
        doc.setJsonPath("/tmp/law-test/articles/loi/loi-2019-045.json");
        repository.save(doc);

        // Créer JSON
        Path jsonFile = jsonDir.resolve("loi-2019-045.json");
        Files.writeString(jsonFile, "{\"articles\": []}");

        // WHEN: Lancement du job
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("type", "loi")
                .addString("documentId", "ALL")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(consolidateJob, jobParameters);

        // THEN: Job réussi mais document inchangé
        assertEquals("COMPLETED", execution.getExitStatus().getExitCode());

        Optional<LawDocumentEntity> updated = repository.findByTypeAndYearAndNumber("loi", 2019, "045");
        assertTrue(updated.isPresent());
        assertEquals(ProcessingStatus.CONSOLIDATED, updated.get().getStatus());
    }

    /**
     * Test 3: Échec si fichier JSON manquant.
     */
    @Test
    void shouldFailIfJsonMissing() throws Exception {
        // GIVEN: Document EXTRACTED SANS fichier JSON
        LawDocumentEntity doc = new LawDocumentEntity();
        doc.setType("loi");
        doc.setYear(2020);
        doc.setNumber("099");
        doc.setDocumentId("loi-2020-099");
        doc.setStatus(ProcessingStatus.EXTRACTED);
        doc.setJsonPath("/tmp/law-test/articles/loi/loi-2020-099.json");
        repository.save(doc);

        // PAS de fichier JSON créé

        // WHEN: Lancement du job
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("type", "loi")
                .addString("documentId", "ALL")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(consolidateJob, jobParameters);

        // THEN: Job réussi mais document en erreur
        assertEquals("COMPLETED", execution.getExitStatus().getExitCode());

        Optional<LawDocumentEntity> updated = repository.findByTypeAndYearAndNumber("loi", 2020, "099");
        assertTrue(updated.isPresent());
        assertEquals(ProcessingStatus.FAILED_CONSOLIDATION, updated.get().getStatus());
        assertNotNull(updated.get().getErrorMessage());
        assertTrue(updated.get().getErrorMessage().contains("introuvable"));
    }

    /**
     * Test 4: Échec si fichier JSON vide.
     */
    @Test
    void shouldFailIfJsonEmpty() throws Exception {
        // GIVEN: Document EXTRACTED avec JSON vide
        LawDocumentEntity doc = new LawDocumentEntity();
        doc.setType("loi");
        doc.setYear(2021);
        doc.setNumber("111");
        doc.setDocumentId("loi-2021-111");
        doc.setStatus(ProcessingStatus.EXTRACTED);
        doc.setJsonPath("/tmp/law-test/articles/loi/loi-2021-111.json");
        repository.save(doc);

        // Créer fichier JSON vide
        Path jsonFile = jsonDir.resolve("loi-2021-111.json");
        Files.writeString(jsonFile, ""); // Vide

        // WHEN: Lancement du job
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("type", "loi")
                .addString("documentId", "ALL")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(consolidateJob, jobParameters);

        // THEN: Job réussi mais document en erreur
        assertEquals("COMPLETED", execution.getExitStatus().getExitCode());

        Optional<LawDocumentEntity> updated = repository.findByTypeAndYearAndNumber("loi", 2021, "111");
        assertTrue(updated.isPresent());
        assertEquals(ProcessingStatus.FAILED_CONSOLIDATION, updated.get().getStatus());
        assertNotNull(updated.get().getErrorMessage());
        assertTrue(updated.get().getErrorMessage().contains("vide"));
    }
}
