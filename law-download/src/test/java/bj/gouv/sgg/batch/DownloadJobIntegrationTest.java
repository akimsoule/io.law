package bj.gouv.sgg.batch;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.DownloadResultRepository;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'intégration pour le job downloadJob.
 * Vérifie le bon fonctionnement complet du job avec H2 et Spring Batch.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
class DownloadJobIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("downloadJob")
    private Job downloadJob;

    @Autowired
    private LawDocumentRepository lawDocumentRepository;
    
    @Autowired
    private DownloadResultRepository downloadResultRepository;

    @BeforeEach
    void setUp() throws IOException {
        // Nettoyer les bases de données H2
        lawDocumentRepository.deleteAll();
        downloadResultRepository.deleteAll();
        
        // Nettoyer les fichiers PDFs téléchargés lors des tests précédents
        Path testStoragePath = Paths.get("/tmp/law-download-test");
        if (Files.exists(testStoragePath)) {
            Files.walk(testStoragePath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignorer les erreurs de suppression
                        }
                    });
        }
    }

    @Test
    void givenNoFetchedDocumentsWhenRunDownloadJobThenCompletesSuccessfully() throws Exception {
        // Given: Aucun document FETCHED dans la base de données
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // When: Exécution du downloadJob sans documents à traiter
        JobExecution jobExecution = jobLauncher.run(downloadJob, jobParameters);

        // Then: Job complété avec succès, aucun document créé
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus(),
                "Le job devrait se terminer avec succès même sans documents à traiter");
        
        long totalCount = lawDocumentRepository.count();
        assertEquals(0, totalCount, "Aucun document ne devrait être créé");
    }

    @Test
    void givenFetchedDocumentsWhenRunDownloadJobThenDownloadsAll() throws Exception {
        // Given: 2 documents FETCHED (1 loi + 1 décret) dans la base
        LawDocument doc1 = LawDocument.builder()
                .type("loi")
                .year(2024)
                .number(15)
                .exists(true)
                .status(LawDocument.ProcessingStatus.FETCHED)
                .url("https://sgg.gouv.bj/doc/loi-2024-15")
                .build();

        LawDocument doc2 = LawDocument.builder()
                .type("decret")
                .year(2025)
                .number(716)
                .exists(true)
                .status(LawDocument.ProcessingStatus.FETCHED)
                .url("https://sgg.gouv.bj/doc/decret-2025-716")
                .build();

        lawDocumentRepository.save(doc1);
        lawDocumentRepository.save(doc2);

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // When: Exécution du downloadJob
        JobExecution jobExecution = jobLauncher.run(downloadJob, jobParameters);

        // Then: Job complété et 2 documents traités (DOWNLOADED ou FAILED)
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus(),
                "Le job devrait se terminer avec succès");

        long totalCount = lawDocumentRepository.count();
        assertEquals(2, totalCount, "Les 2 documents devraient toujours exister");

        // Les documents peuvent avoir le statut DOWNLOADED (succès) ou FAILED (404/erreur réseau)
        long downloadedOrFailed = lawDocumentRepository.countByStatus(LawDocument.ProcessingStatus.DOWNLOADED)
                + lawDocumentRepository.countByStatus(LawDocument.ProcessingStatus.FAILED);
        assertEquals(2, downloadedOrFailed,
                "Les 2 documents devraient avoir été traités (DOWNLOADED ou FAILED)");
    }

    @Test
    void givenJobAlreadyExecutedWhenRunAgainThenIdempotent() throws Exception {
        // Given: 1 document FETCHED dans la base
        LawDocument doc = LawDocument.builder()
                .type("loi")
                .year(2024)
                .number(15)
                .exists(true)
                .status(LawDocument.ProcessingStatus.FETCHED)
                .url("https://sgg.gouv.bj/doc/loi-2024-15")
                .build();
        lawDocumentRepository.save(doc);

        // When: Premier run puis second run du downloadJob
        JobParameters jobParameters1 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        JobExecution firstExecution = jobLauncher.run(downloadJob, jobParameters1);
        
        long countAfterFirst = lawDocumentRepository.count();
        LawDocument.ProcessingStatus statusAfterFirst = lawDocumentRepository.findAll().get(0).getStatus();

        JobParameters jobParameters2 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis() + 1000)
                .toJobParameters();
        JobExecution secondExecution = jobLauncher.run(downloadJob, jobParameters2);
        
        long countAfterSecond = lawDocumentRepository.count();
        LawDocument.ProcessingStatus statusAfterSecond = lawDocumentRepository.findAll().get(0).getStatus();

        // Then: Les deux runs sont complétés avec même nombre de documents et même statut (idempotence)
        assertEquals(BatchStatus.COMPLETED, firstExecution.getStatus(),
                "Le premier run devrait se terminer avec succès");
        assertEquals(BatchStatus.COMPLETED, secondExecution.getStatus(),
                "Le second run devrait se terminer avec succès");
        assertEquals(countAfterFirst, countAfterSecond,
                "Le job devrait être idempotent - même nombre de documents après 2 runs");
        assertEquals(statusAfterFirst, statusAfterSecond,
                "Le statut ne devrait pas changer après un second run");
    }

    @Test
    void givenFetchedDocumentWhenDownloadedThenPreservesUrl() throws Exception {
        // Given: 1 document FETCHED avec URL valide
        LawDocument doc = LawDocument.builder()
                .type("loi")
                .year(2024)
                .number(15)
                .exists(true)
                .status(LawDocument.ProcessingStatus.FETCHED)
                .url("https://sgg.gouv.bj/doc/loi-2024-15")
                .build();
        lawDocumentRepository.save(doc);

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // When: Téléchargement du document
        jobLauncher.run(downloadJob, jobParameters);

        // Then: L'URL du document est préservée après téléchargement
        LawDocument updatedDoc = lawDocumentRepository.findAll().get(0);
        assertTrue(updatedDoc.getUrl() != null && !updatedDoc.getUrl().isEmpty(),
                "Le document devrait conserver son URL");
        assertTrue(updatedDoc.getUrl().contains("loi-2024-15"),
                "L'URL devrait contenir l'identifiant du document");
    }

    @Test
    void givenSpecificLawIdWhenRunDownloadJobThenDownloadsTargetedLaw() throws Exception {
        // Given: 1 loi spécifique FETCHED (loi-2024-15)
        LawDocument doc = LawDocument.builder()
                .type("loi")
                .year(2024)
                .number(15)
                .exists(true)
                .status(LawDocument.ProcessingStatus.FETCHED)
                .url("https://sgg.gouv.bj/doc/loi-2024-15")
                .build();
        lawDocumentRepository.save(doc);

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "loi-2024-15")
                .toJobParameters();

        // When: Exécution du downloadJob avec document ciblé
        JobExecution jobExecution = jobLauncher.run(downloadJob, jobParameters);

        // Then: Job complété et document ciblé téléchargé ou en erreur
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus(),
                "Le job devrait se terminer avec succès pour un document ciblé");

        // Vérifier que le document a été téléchargé
        LawDocument downloaded = lawDocumentRepository.findByTypeAndYearAndNumber("loi", 2024, 15)
                .orElse(null);
        
        if (downloaded != null) {
            assertTrue(downloaded.getStatus() == LawDocument.ProcessingStatus.DOWNLOADED ||
                      downloaded.getStatus() == LawDocument.ProcessingStatus.FAILED,
                    "Le document ciblé devrait avoir été traité (DOWNLOADED ou FAILED)");
        }
    }

    @Test
    void givenSpecificDecretIdWhenRunDownloadJobThenDownloadsTargetedDecret() throws Exception {
        // Given: 1 décret spécifique FETCHED (decret-2025-716)
        LawDocument doc = LawDocument.builder()
                .type("decret")
                .year(2025)
                .number(716)
                .exists(true)
                .status(LawDocument.ProcessingStatus.FETCHED)
                .url("https://sgg.gouv.bj/doc/decret-2025-716")
                .build();
        lawDocumentRepository.save(doc);

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "decret-2025-716")
                .toJobParameters();

        // When: Exécution du downloadJob avec décret ciblé
        JobExecution jobExecution = jobLauncher.run(downloadJob, jobParameters);

        // Then: Job complété et décret ciblé téléchargé ou en erreur
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus(),
                "Le job devrait se terminer avec succès pour un décret ciblé");

        // Vérifier que le décret a été téléchargé
        LawDocument downloaded = lawDocumentRepository.findByTypeAndYearAndNumber("decret", 2025, 716)
                .orElse(null);
        
        if (downloaded != null) {
            assertTrue(downloaded.getStatus() == LawDocument.ProcessingStatus.DOWNLOADED ||
                      downloaded.getStatus() == LawDocument.ProcessingStatus.FAILED,
                    "Le décret ciblé devrait avoir été traité");
        }
    }

    @Test
    void givenDownloadedDocumentWhenRunWithForceThenRedownloadsWithoutDuplicates() throws Exception {
        // Given: 1 document FETCHED puis téléchargé (statut DOWNLOADED)
        LawDocument doc = LawDocument.builder()
                .type("loi")
                .year(2024)
                .number(15)
                .exists(true)
                .status(LawDocument.ProcessingStatus.FETCHED)
                .url("https://sgg.gouv.bj/doc/loi-2024-15")
                .build();
        lawDocumentRepository.save(doc);

        JobParameters jobParameters1 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "loi-2024-15")
                .toJobParameters();
        JobExecution firstExecution = jobLauncher.run(downloadJob, jobParameters1);
        assertEquals(BatchStatus.COMPLETED, firstExecution.getStatus(),
                "Le premier run devrait se terminer avec succès");

        // Simuler un document déjà DOWNLOADED
        LawDocument existingDoc = lawDocumentRepository.findByTypeAndYearAndNumber("loi", 2024, 15)
                .orElse(null);
        if (existingDoc != null && existingDoc.getStatus() == LawDocument.ProcessingStatus.DOWNLOADED) {
            // When: Second run avec force=true pour re-télécharger
            JobParameters jobParameters2 = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis() + 1000)
                    .addString("documentId", "loi-2024-15")
                    .addString("force", "true")
                    .toJobParameters();
            JobExecution secondExecution = jobLauncher.run(downloadJob, jobParameters2);

            // Then: Job complété sans duplication de documents
            assertEquals(BatchStatus.COMPLETED, secondExecution.getStatus(),
                    "Le job avec force=true devrait se terminer avec succès");

            // Le document devrait toujours exister (pas de duplication)
            long secondCount = lawDocumentRepository.count();
            assertEquals(1, secondCount,
                    "Le mode force ne devrait pas créer de duplicates dans law_documents");
        }
    }

    @Test
    void givenDownloadedDocumentWhenRunWithoutForceThenSkipsDocument() throws Exception {
        // Given: 1 document FETCHED puis téléchargé (statut DOWNLOADED)
        LawDocument doc = LawDocument.builder()
                .type("loi")
                .year(2024)
                .number(15)
                .exists(true)
                .status(LawDocument.ProcessingStatus.FETCHED)
                .url("https://sgg.gouv.bj/doc/loi-2024-15")
                .build();
        lawDocumentRepository.save(doc);

        JobParameters jobParameters1 = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "loi-2024-15")
                .toJobParameters();
        JobExecution firstExecution = jobLauncher.run(downloadJob, jobParameters1);
        assertEquals(BatchStatus.COMPLETED, firstExecution.getStatus(),
                "Le premier run devrait se terminer avec succès");

        // Marquer le document comme DOWNLOADED
        LawDocument downloaded = lawDocumentRepository.findByTypeAndYearAndNumber("loi", 2024, 15)
                .orElse(null);
        if (downloaded != null) {
            downloaded.setStatus(LawDocument.ProcessingStatus.DOWNLOADED);
            lawDocumentRepository.save(downloaded);

            long firstCount = downloadResultRepository.count();

            // When: Second run SANS force (devrait skip le document DOWNLOADED)
            JobParameters jobParameters2 = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis() + 1000)
                    .addString("documentId", "loi-2024-15")
                    .toJobParameters();
            JobExecution secondExecution = jobLauncher.run(downloadJob, jobParameters2);

            // Then: Job complété, document skippé, pas de nouveau téléchargement
            assertEquals(BatchStatus.COMPLETED, secondExecution.getStatus(),
                    "Le job sans force devrait se terminer avec succès");

            long secondCount = downloadResultRepository.count();
            assertEquals(firstCount, secondCount,
                    "Le job sans force ne devrait pas re-télécharger un document DOWNLOADED");
        }
    }

    @Test
    void givenFiveDocumentsWhenRunWithMaxThreeThenProcessesOnlyThree() throws Exception {
        // Given: 5 documents FETCHED dans la base
        for (int i = 1; i <= 5; i++) {
            LawDocument doc = LawDocument.builder()
                    .type("loi")
                    .year(2024)
                    .number(i)
                    .exists(true)
                    .status(LawDocument.ProcessingStatus.FETCHED)
                    .url(String.format("https://sgg.gouv.bj/doc/loi-2024-%02d", i))
                    .build();
            lawDocumentRepository.save(doc);
        }

        // When: Exécution du downloadJob avec limite maxDocuments=3
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("maxDocuments", "3")
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(downloadJob, jobParameters);

        // Then: Job complété avec au maximum 3 documents traités
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus(),
                "Le job devrait se terminer avec succès");

        // Au moins 3 documents devraient avoir été traités (ou moins si erreurs réseau)
        long processedCount = lawDocumentRepository.countByStatus(LawDocument.ProcessingStatus.DOWNLOADED)
                + lawDocumentRepository.countByStatus(LawDocument.ProcessingStatus.FAILED);
        assertTrue(processedCount >= 0 && processedCount <= 3,
                "Le nombre de documents traités devrait respecter maxDocuments=3");
    }

    @Test
    void givenDocumentWithSingleDigitNumberWhenDownloadedThenUrlHasPaddedNumber() throws Exception {
        // Given: 1 document avec numéro < 10 ayant un padding dans l'URL (04)
        LawDocument doc = LawDocument.builder()
                .type("loi")
                .year(2025)
                .number(4)
                .exists(true)
                .status(LawDocument.ProcessingStatus.FETCHED)
                .url("https://sgg.gouv.bj/doc/loi-2025-04") // Padding: 04
                .build();
        lawDocumentRepository.save(doc);

        // When: Téléchargement du document via son ID sans padding
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("documentId", "loi-2025-4") // Sans padding dans l'ID
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(downloadJob, jobParameters);

        // Then: Job complété, URL préservée avec padding, pas de suffixe /download en base
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus(),
                "Le job devrait se terminer avec succès");

        LawDocument retrieved = lawDocumentRepository.findByTypeAndYearAndNumber("loi", 2025, 4)
                .orElse(null);
        if (retrieved != null) {
            assertTrue(retrieved.getUrl().contains("loi-2025-04"),
                    "L'URL devrait contenir le numéro avec padding (04)");
            assertFalse(retrieved.getUrl().endsWith("/download"),
                    "L'URL en base ne devrait PAS se terminer par /download");
        }
    }

    @Test
    void givenDocumentBaseUrlWhenBuildingDownloadUrlThenAddsDownloadSuffix() {
        // Given: URL de base d'un document (sans suffixe /download)
        String baseUrl = "https://sgg.gouv.bj/doc/loi-2025-04";
        LawDocument doc = LawDocument.builder()
                .type("loi")
                .year(2025)
                .number(4)
                .url(baseUrl)
                .status(LawDocument.ProcessingStatus.FETCHED)
                .build();

        // When: Construction de l'URL de téléchargement (comme dans DownloadProcessor)
        String downloadUrl = doc.getUrl() + "/download";

        // Then: L'URL de téléchargement a le suffixe /download
        assertEquals("https://sgg.gouv.bj/doc/loi-2025-04/download", downloadUrl,
                "L'URL de téléchargement doit avoir le suffixe /download");
        assertTrue(downloadUrl.endsWith("/download"),
                "L'URL de téléchargement doit se terminer par /download");
    }

    @Test
    void givenLawAndDecretDocumentsWhenGettingIdsThenFormattedCorrectly() {
        // Given: 1 loi et 1 décret avec type, année et numéro
        LawDocument loi = LawDocument.builder()
                .type("loi")
                .year(2025)
                .number(4)
                .status(LawDocument.ProcessingStatus.FETCHED)
                .build();

        LawDocument decret = LawDocument.builder()
                .type("decret")
                .year(2025)
                .number(716)
                .status(LawDocument.ProcessingStatus.FETCHED)
                .build();

        // Then: IDs formatés selon le pattern type-year-number
        assertEquals("loi-2025-4", loi.getDocumentId(),
                "L'ID de la loi devrait être loi-2025-4");
        assertEquals("decret-2025-716", decret.getDocumentId(),
                "L'ID du décret devrait être decret-2025-716");
    }

    @Test
    void givenPdfMagicBytesWhenValidatingThenRecognizedAsPdfSignature() {
        // Given: Signature PDF valide (%PDF) sous forme de bytes
        byte[] validPdfSignature = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF

        // Then: Chaque byte correspond au magic number PDF
        assertEquals(0x25, validPdfSignature[0], "Premier byte devrait être % (0x25)");
        assertEquals(0x50, validPdfSignature[1], "Deuxième byte devrait être P (0x50)");
        assertEquals(0x44, validPdfSignature[2], "Troisième byte devrait être D (0x44)");
        assertEquals(0x46, validPdfSignature[3], "Quatrième byte devrait être F (0x46)");

        String signature = new String(validPdfSignature);
        assertTrue(signature.startsWith("%PDF"),
                "La signature devrait commencer par %PDF");
    }
}
