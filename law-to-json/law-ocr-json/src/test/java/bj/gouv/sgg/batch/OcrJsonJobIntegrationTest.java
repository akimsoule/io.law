package bj.gouv.sgg.batch;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.model.OcrExtractionResult;
import bj.gouv.sgg.repository.LawDocumentRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration du job OcrJson avec fichiers OCR réels.
 * Vérifie les transitions d'état OCRED → EXTRACTED avec extraction d'articles réelle.
 */
@SpringBootTest
@ActiveProfiles("test")
class OcrJsonJobIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job ocrJsonJob;

    @Autowired
    private LawDocumentRepository repository;

    // Gson avec adapter pour LocalDate
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();

    /**
     * Adapter pour sérialiser/désérialiser LocalDate en ISO format
     */
    private static class LocalDateAdapter extends TypeAdapter<LocalDate> {
        @Override
        public void write(JsonWriter out, LocalDate date) throws IOException {
            if (date == null) {
                out.nullValue();
            } else {
                out.value(date.toString()); // ISO format: yyyy-MM-dd
            }
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            String dateStr = in.nextString();
            return dateStr != null ? LocalDate.parse(dateStr) : null;
        }
    }
    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("law-ocr-json-test");
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
    }

    @Test
    void shouldProcessOcredDocumentToExtracted() throws Exception {
        // Given: Document OCRED avec fichier OCR réel
        File goodOcr = new ClassPathResource("good_ocr/loi-2012-001.txt").getFile();
        
        LawDocumentEntity doc = LawDocumentEntity.builder()
                .type("loi")
                .year(2012)
                .number("001")
                .documentId("loi-2012-001")
                .status(ProcessingStatus.OCRED)
                .ocrPath(goodOcr.getAbsolutePath())
                .build();
        repository.saveAndFlush(doc);

        // When: Lancement du job avec extraction d'articles réelle
        JobParameters params = new JobParametersBuilder()
                .addString("type", "loi")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(ocrJsonJob, params);

        // Then: Job SUCCESS
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // And: Document maintenant EXTRACTED
        LawDocumentEntity updated = repository.findByTypeAndYearAndNumber("loi", 2012, "001").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProcessingStatus.EXTRACTED);
        assertThat(updated.getJsonPath()).isNotNull();
        
        // And: Fichier JSON créé et contient des articles
        File jsonFile = new File(updated.getJsonPath());
        assertThat(jsonFile).exists();
        String jsonContent = Files.readString(jsonFile.toPath());
        assertThat(jsonContent).isNotEmpty();
        
        // And: JSON est valide et contient des articles
        OcrExtractionResult result = gson.fromJson(jsonContent, OcrExtractionResult.class);
        assertThat(result).isNotNull();
        assertThat(result.getArticles()).isNotEmpty();
        assertThat(result.getMetadata()).isNotNull();
        assertThat(result.getConfidence()).isGreaterThan(0.0);
        
        // Log pour debug
        System.out.println("✅ Articles extracted: " + result.getArticles().size());
        System.out.println("✅ Confidence: " + result.getConfidence());
        System.out.println("✅ Metadata: " + result.getMetadata());
    }

    @Test
    void shouldSkipDocumentWithExistingJsonPath() throws Exception {
        // Given: Document OCRED avec jsonPath déjà défini
        File goodOcr = new ClassPathResource("good_ocr/loi-2012-001.txt").getFile();
        Path existingJson = tempDir.resolve("existing-json.json");
        Files.write(existingJson, "{\"articles\":[],\"documentId\":\"loi-2012-002\"}".getBytes());

        LawDocumentEntity doc = LawDocumentEntity.builder()
                .type("loi")
                .year(2012)
                .number("002")
                .documentId("loi-2012-002")
                .status(ProcessingStatus.OCRED)
                .ocrPath(goodOcr.getAbsolutePath())
                .jsonPath(existingJson.toString())
                .build();
        repository.saveAndFlush(doc);

        // When: Lancement du job
        JobParameters params = new JobParametersBuilder()
                .addString("type", "loi")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(ocrJsonJob, params);

        // Then: Job SUCCESS
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // And: JSON existant non modifié (idempotence)
        String existingContent = Files.readString(existingJson);
        assertThat(existingContent).contains("loi-2012-002");
        assertThat(existingContent).doesNotContain("Article 1");
    }

    @Test
    void shouldHandleEmptyOcrText() throws Exception {
        // Given: Document OCRED avec fichier OCR vide
        Path emptyOcr = tempDir.resolve("empty.txt");
        Files.write(emptyOcr, "".getBytes());
        
        LawDocumentEntity doc = LawDocumentEntity.builder()
                .type("loi")
                .year(2012)
                .number("003")
                .documentId("loi-2012-003")
                .status(ProcessingStatus.OCRED)
                .ocrPath(emptyOcr.toString())
                .build();
        repository.saveAndFlush(doc);

        // When: Lancement du job
        JobParameters params = new JobParametersBuilder()
                .addString("type", "loi")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(ocrJsonJob, params);

        // Then: Job SUCCESS (ne crash pas)
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // And: Document marqué en erreur
        LawDocumentEntity updated = repository.findByTypeAndYearAndNumber("loi", 2012, "003").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProcessingStatus.FAILED_EXTRACTION);
        assertThat(updated.getErrorMessage()).contains("empty");
    }

    @Test
    void shouldHandleMissingOcrFile() throws Exception {
        // Given: Document OCRED avec ocrPath inexistant
        LawDocumentEntity doc = LawDocumentEntity.builder()
                .type("loi")
                .year(2012)
                .number("004")
                .documentId("loi-2012-004")
                .status(ProcessingStatus.OCRED)
                .ocrPath("/nonexistent/path/loi-2012-004.txt")
                .build();
        repository.saveAndFlush(doc);

        // When: Lancement du job
        JobParameters params = new JobParametersBuilder()
                .addString("type", "loi")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(ocrJsonJob, params);

        // Then: Job SUCCESS (ne crash pas)
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // And: Document marqué en erreur
        LawDocumentEntity updated = repository.findByTypeAndYearAndNumber("loi", 2012, "004").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProcessingStatus.FAILED_EXTRACTION);
        assertThat(updated.getErrorMessage()).contains("not found");
    }
}
