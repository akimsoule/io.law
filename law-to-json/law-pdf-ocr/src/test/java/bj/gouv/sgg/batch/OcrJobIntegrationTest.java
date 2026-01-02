package bj.gouv.sgg.batch;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration du job OCR avec PDFs réels et Tesseract.
 * Vérifie les transitions d'état DOWNLOADED → OCRED_V2 avec extraction OCR réelle.
 */
@SpringBootTest
@ActiveProfiles("test")
class OcrJobIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job ocrJob;

    @Autowired
    private LawDocumentRepository repository;

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("law-ocr-test");
    }

    @AfterEach
    void cleanup() throws IOException {
        repository.deleteAll();
        // Nettoyer les fichiers temporaires
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> -a.compareTo(b)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
        // Nettoyer les fichiers de test dans data/
        Path pdfsDir = Path.of(System.getProperty("user.dir")).resolve("data").resolve("pdfs").resolve("loi");
        if (Files.exists(pdfsDir)) {
            Files.walk(pdfsDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
        Path ocrDir = Path.of(System.getProperty("user.dir")).resolve("data").resolve("ocr").resolve("loi");
        if (Files.exists(ocrDir)) {
            Files.walk(ocrDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    @Test
    void shouldProcessDownloadedDocumentToOcred() throws Exception {
        // Given: Document DOWNLOADED avec PDF réel
        ClassPathResource resource = new ClassPathResource("good_pdf/loi-2025-17.pdf");
        
        // Copy to the expected location: data/pdfs/loi/loi-2025-17.pdf
        Path pdfsDir = Path.of(System.getProperty("user.dir")).resolve("data").resolve("pdfs").resolve("loi");
        Files.createDirectories(pdfsDir);
        Path expectedPdf = pdfsDir.resolve("loi-2025-17.pdf");
        Files.copy(resource.getInputStream(), expectedPdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        LawDocumentEntity doc = LawDocumentEntity.builder()
                .type("loi")
                .year(2025)
                .number("17")
                .documentId("loi-2025-17")
                .status(ProcessingStatus.DOWNLOADED)
                .pdfPath(expectedPdf.toString())
                .build();
        repository.saveAndFlush(doc);

        // When: Lancement du job avec Tesseract OCR réel
        JobParameters params = new JobParametersBuilder()
                .addString("type", "loi")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(ocrJob, params);

        // Then: Job SUCCESS
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // And: Document maintenant OCRED_V2
        LawDocumentEntity updated = repository.findByTypeAndYearAndNumber("loi", 2025, "17").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProcessingStatus.OCRED_V2);
        assertThat(updated.getOcrPath()).isNotNull();
        
        // And: Fichier OCR créé et contient du texte
        File ocrFile = new File(updated.getOcrPath());
        assertThat(ocrFile).exists();
        String ocrContent = Files.readString(ocrFile.toPath());
        assertThat(ocrContent).isNotEmpty();
        assertThat(ocrContent.length()).isGreaterThan(100); // Au moins 100 caractères extraits
    }

    @Test
    void shouldSkipDocumentWithExistingOcrPath() throws Exception {
        // Given: Document DOWNLOADED avec PDF réel
        ClassPathResource resource = new ClassPathResource("good_pdf/loi-2025-17.pdf");
        
        // Copy to the expected location: data/pdfs/loi/loi-2025-018.pdf
        Path pdfsDir = Path.of(System.getProperty("user.dir")).resolve("data").resolve("pdfs").resolve("loi");
        Files.createDirectories(pdfsDir);
        Path expectedPdf = pdfsDir.resolve("loi-2025-018.pdf");
        Files.copy(resource.getInputStream(), expectedPdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        // Create existing OCR file
        Path ocrDir = Path.of(System.getProperty("user.dir")).resolve("data").resolve("ocr").resolve("loi");
        Files.createDirectories(ocrDir);
        Path existingOcr = ocrDir.resolve("loi-2025-018.txt");
        Files.write(existingOcr, "Texte OCR déjà extrait précédemment".getBytes());

        LawDocumentEntity doc = LawDocumentEntity.builder()
                .type("loi")
                .year(2025)
                .number("018")
                .documentId("loi-2025-018")
                .status(ProcessingStatus.DOWNLOADED)
                .pdfPath(expectedPdf.toString())
                .ocrPath(existingOcr.toString())
                .build();
        repository.saveAndFlush(doc);

        // When: Lancement du job
        JobParameters params = new JobParametersBuilder()
                .addString("type", "loi")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(ocrJob, params);

        // Then: Job SUCCESS
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // And: OCR existant non modifié (idempotence)
        String existingContent = Files.readString(existingOcr);
        assertThat(existingContent).isEqualTo("Texte OCR déjà extrait précédemment");
    }

    @Test
    void shouldHandleCorruptedPdf() throws Exception {
        // Given: Document DOWNLOADED avec PDF corrompu
        ClassPathResource resource = new ClassPathResource("corrupted_pdf/loi-2012-43.pdf");
        
        // Copy to the expected location: data/pdfs/loi/loi-2012-043.pdf
        Path pdfsDir = Path.of(System.getProperty("user.dir")).resolve("data").resolve("pdfs").resolve("loi");
        Files.createDirectories(pdfsDir);
        Path expectedPdf = pdfsDir.resolve("loi-2012-043.pdf");
        Files.copy(resource.getInputStream(), expectedPdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        LawDocumentEntity doc = LawDocumentEntity.builder()
                .type("loi")
                .year(2012)
                .number("043")
                .documentId("loi-2012-043")
                .status(ProcessingStatus.DOWNLOADED)
                .pdfPath(expectedPdf.toString())
                .build();
        repository.saveAndFlush(doc);

        // When: Lancement du job
        JobParameters params = new JobParametersBuilder()
                .addString("type", "loi")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(ocrJob, params);

        // Then: Job SUCCESS (ne crash pas)
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // And: Document marqué en erreur
        LawDocumentEntity updated = repository.findByTypeAndYearAndNumber("loi", 2012, "043").orElseThrow();
        assertThat(updated.getStatus()).isIn(ProcessingStatus.FAILED_OCR, ProcessingStatus.FAILED_CORRUPTED);
        assertThat(updated.getErrorMessage()).isNotNull();
    }
}
