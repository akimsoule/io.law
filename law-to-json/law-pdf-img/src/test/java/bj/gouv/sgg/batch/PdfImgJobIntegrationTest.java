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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PdfImgJobIntegrationTest {

    private static final String DOCUMENT_ID = "loi-2025-17";

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job pdfToImagesJob;

    @Autowired
    private LawDocumentRepository repository;

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("pdfimg-test");
    }

    @AfterEach
    void cleanup() throws IOException {
        repository.deleteAll();
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception e) {
                            // ignore deletion failures during cleanup of temporary test directory
                        }
                    });
        }
    }

    @Test
    void givenDownloadedPdfWhenJobRunsThenCreatesImagesAndMarksImaged() throws Exception {
        // Given: a downloaded PDF available on disk
        ClassPathResource resource = new ClassPathResource("pdf/loi-1961-20.pdf");

        // Copy to the expected location: data/pdfs/loi/loi-2025-17.pdf
        Path pdfsDir = Path.of(System.getProperty("user.dir")).resolve("data").resolve("pdfs").resolve("loi");
        Files.createDirectories(pdfsDir);
        Path expectedPdf = pdfsDir.resolve(DOCUMENT_ID + ".pdf");
        Files.copy(resource.getInputStream(), expectedPdf, StandardCopyOption.REPLACE_EXISTING);

        // And: no pre-existing images for this document (tests may leave artifacts)
        Path imagesRoot = Path.of(System.getProperty("user.dir")).resolve("data").resolve("images");
        Path docImages = imagesRoot.resolve(DOCUMENT_ID);
        if (Files.exists(docImages)) {
            Files.walk(docImages)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception e) {
                            // ignore deletion failures of pre-existing test artifacts
                        }
                    });
        }

        LawDocumentEntity doc = LawDocumentEntity.builder()
                .type("loi")
                .year(2025)
                .number("17")
                .documentId(DOCUMENT_ID)
                .status(ProcessingStatus.DOWNLOADED)
                .pdfPath(expectedPdf.toString())
                .build();
        repository.saveAndFlush(doc);

        // When: the pdfToImages job is launched
        JobParameters params = new JobParametersBuilder()
                .addString("type", "loi")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(pdfToImagesJob, params);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Then: images are created
        Path imagesDir = Path.of(System.getProperty("user.dir")).resolve("data").resolve("images").resolve(DOCUMENT_ID);
        assertThat(Files.exists(imagesDir)).isTrue();
        assertThat(Files.exists(imagesDir.resolve("page-0001.png"))).isTrue();

        // And: the entity is marked IMAGED
        LawDocumentEntity updated = repository.findByDocumentId(DOCUMENT_ID).orElseThrow();
        assertThat(updated.hasOtherProcessingStatus(bj.gouv.sgg.entity.OtherProcessingStatus.IMAGED)).isTrue();
    }
}
