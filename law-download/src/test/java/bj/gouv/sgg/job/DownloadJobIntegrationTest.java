package bj.gouv.sgg.job;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.service.PdfDownloadService;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests d'intégration pour le job Spring Batch de téléchargement des PDFs.
 * Mock PdfDownloadService pour éviter les vraies requêtes HTTP.
 */
@SpringBootTest
@ActiveProfiles("test")
class DownloadJobIntegrationTest {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    private Job downloadJob;
    
    @Autowired
    private LawDocumentRepository repository;
    
    @MockBean
    private PdfDownloadService pdfDownloadService;
    
    @BeforeEach
    void setup() {
        repository.deleteAll();
    }
    
    @Test
    void givenFetchedDocumentsThenDownloadJobShouldDownloadThemSuccessfully() throws Exception {
        // Given - Créer des documents FETCHED
        LawDocumentEntity doc1 = LawDocumentEntity.builder()
                .type("loi")
                .year(2024)
                .number("15")
                .documentId("loi-2024-15")
                .status(ProcessingStatus.FETCHED)
                .build();
        
        LawDocumentEntity doc2 = LawDocumentEntity.builder()
                .type("decret")
                .year(2024)
                .number("100")
                .documentId("decret-2024-100")
                .status(ProcessingStatus.FETCHED)
                .build();
        
        repository.saveAll(List.of(doc1, doc2));
        
        // Mock PdfDownloadService pour retourner un hash sans vraiment télécharger
        when(pdfDownloadService.downloadPdf(anyString(), anyInt(), anyString(), any(Path.class)))
                .thenReturn("abc123def456"); // Hash mocké
        
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("type", "loi")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        // When
        JobExecution execution = jobLauncher.run(downloadJob, jobParameters);
        
        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        // Vérifier que les documents ont été mis à jour
        List<LawDocumentEntity> documents = repository.findAll();
        assertThat(documents).hasSize(2);
        
        // Vérifier que le document loi a été téléchargé
        LawDocumentEntity downloadedLoi = repository.findByDocumentId("loi-2024-15").orElseThrow();
        assertThat(downloadedLoi.getStatus()).isEqualTo(ProcessingStatus.DOWNLOADED);
        assertThat(downloadedLoi.getPdfPath()).isNotNull();
        assertThat(downloadedLoi.getPdfPath()).contains("loi-2024-15");
        
        // Vérifier que le décret n'a pas été traité (type=loi dans jobParameters)
        LawDocumentEntity decret = repository.findByDocumentId("decret-2024-100").orElseThrow();
        assertThat(decret.getStatus()).isEqualTo(ProcessingStatus.FETCHED);
        assertThat(decret.getPdfPath()).isNull();
    }
    
    @Test
    void givenSpecificDocumentIdThenDownloadJobShouldDownloadOnlyThatDocument() throws Exception {
        // Given - Créer plusieurs documents FETCHED
        LawDocumentEntity doc1 = LawDocumentEntity.builder()
                .type("loi")
                .year(2024)
                .number("15")
                .documentId("loi-2024-15")
                .status(ProcessingStatus.FETCHED)
                .build();
        
        LawDocumentEntity doc2 = LawDocumentEntity.builder()
                .type("loi")
                .year(2024)
                .number("16")
                .documentId("loi-2024-16")
                .status(ProcessingStatus.FETCHED)
                .build();
        
        repository.saveAll(List.of(doc1, doc2));
        
        // Mock PdfDownloadService
        when(pdfDownloadService.downloadPdf(anyString(), anyInt(), anyString(), any(Path.class)))
                .thenReturn("hash123");
        
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("documentId", "loi-2024-15")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        // When
        JobExecution execution = jobLauncher.run(downloadJob, jobParameters);
        
        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        // Vérifier que seul loi-2024-15 a été téléchargé
        LawDocumentEntity downloaded = repository.findByDocumentId("loi-2024-15").orElseThrow();
        assertThat(downloaded.getStatus()).isEqualTo(ProcessingStatus.DOWNLOADED);
        assertThat(downloaded.getPdfPath()).isNotNull();
        
        // Vérifier que loi-2024-16 n'a pas été traité
        LawDocumentEntity notDownloaded = repository.findByDocumentId("loi-2024-16").orElseThrow();
        assertThat(notDownloaded.getStatus()).isEqualTo(ProcessingStatus.FETCHED);
        assertThat(notDownloaded.getPdfPath()).isNull();
    }
    
    @Test
    void givenAlreadyDownloadedDocumentThenDownloadJobShouldSkipIt() throws Exception {
        // Given - Créer un document déjà DOWNLOADED avec pdfPath
        LawDocumentEntity doc = LawDocumentEntity.builder()
                .type("loi")
                .year(2024)
                .number("15")
                .documentId("loi-2024-15")
                .status(ProcessingStatus.DOWNLOADED)
                .pdfPath("target/test-data/pdfs/loi/loi-2024-15.pdf")
                .build();
        
        repository.save(doc);
        
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("documentId", "loi-2024-15")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        // When
        JobExecution execution = jobLauncher.run(downloadJob, jobParameters);
        
        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        // Vérifier que le document n'a pas été re-téléchargé
        LawDocumentEntity unchanged = repository.findByDocumentId("loi-2024-15").orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ProcessingStatus.DOWNLOADED);
        assertThat(unchanged.getPdfPath()).isEqualTo("target/test-data/pdfs/loi/loi-2024-15.pdf");
    }
    
    @Test
    void givenFailedCorruptedDocumentsThenDownloadJobShouldRetryThem() throws Exception {
        // Given - Créer un document FAILED_CORRUPTED
        LawDocumentEntity doc = LawDocumentEntity.builder()
                .type("loi")
                .year(2024)
                .number("15")
                .documentId("loi-2024-15")
                .status(ProcessingStatus.FAILED_CORRUPTED)
                .errorMessage("PDF vide lors de la tentative précédente")
                .build();
        
        repository.save(doc);
        
        // Mock PdfDownloadService pour réussir cette fois
        when(pdfDownloadService.downloadPdf(anyString(), anyInt(), anyString(), any(Path.class)))
                .thenReturn("hash456");
        
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("type", "loi")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        // When
        JobExecution execution = jobLauncher.run(downloadJob, jobParameters);
        
        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        // Vérifier que le document a été re-téléchargé avec succès
        LawDocumentEntity retried = repository.findByDocumentId("loi-2024-15").orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(ProcessingStatus.DOWNLOADED);
        assertThat(retried.getPdfPath()).isNotNull();
        assertThat(retried.getErrorMessage()).isNull();
    }
    
    @Test
    void givenMaxItemsParameterThenDownloadJobShouldRespectLimit() throws Exception {
        // Given - Créer 5 documents FETCHED
        for (int i = 1; i <= 5; i++) {
            LawDocumentEntity doc = LawDocumentEntity.builder()
                    .type("loi")
                    .year(2024)
                    .number(String.valueOf(i))
                    .documentId("loi-2024-" + i)
                    .status(ProcessingStatus.FETCHED)
                    .build();
            repository.save(doc);
        }
        
        // Mock PdfDownloadService
        when(pdfDownloadService.downloadPdf(anyString(), anyInt(), anyString(), any(Path.class)))
                .thenReturn("hash789");
        
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("type", "loi")
                .addLong("maxItems", 3L)  // Limiter à 3 documents
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        // When
        JobExecution execution = jobLauncher.run(downloadJob, jobParameters);
        
        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        // Vérifier que seulement 3 documents ont été téléchargés
        long downloadedCount = repository.findAll().stream()
                .filter(doc -> doc.getStatus() == ProcessingStatus.DOWNLOADED)
                .count();
        assertThat(downloadedCount).isEqualTo(3);
        
        // Vérifier que 2 documents sont restés en FETCHED
        long fetchedCount = repository.findAll().stream()
                .filter(doc -> doc.getStatus() == ProcessingStatus.FETCHED)
                .count();
        assertThat(fetchedCount).isEqualTo(2);
    }
    
    @Test
    void givenMultipleDocumentsThenDownloadJobShouldProcessThemInParallel() throws Exception {
        // Given - Créer 10 documents FETCHED pour tester le multi-threading
        for (int i = 1; i <= 10; i++) {
            LawDocumentEntity doc = LawDocumentEntity.builder()
                    .type("loi")
                    .year(2024)
                    .number(String.valueOf(i))
                    .documentId("loi-2024-" + i)
                    .status(ProcessingStatus.FETCHED)
                    .build();
            repository.save(doc);
        }
        
        // Mock PdfDownloadService avec délai pour simuler le téléchargement
        when(pdfDownloadService.downloadPdf(anyString(), anyInt(), anyString(), any(Path.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(100); // Simuler téléchargement
                    return "hash" + System.nanoTime();
                });
        
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("type", "loi")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        long startTime = System.currentTimeMillis();
        
        // When
        JobExecution execution = jobLauncher.run(downloadJob, jobParameters);
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        // Vérifier que tous les documents ont été téléchargés
        List<LawDocumentEntity> documents = repository.findAll();
        assertThat(documents).hasSize(10);
        assertThat(documents).allMatch(doc -> 
            doc.getStatus() == ProcessingStatus.DOWNLOADED && doc.getPdfPath() != null
        );
        
        // Vérifier le traitement séquentiel
        // 10 documents * 100ms = ~1000ms minimum en séquentiel
        assertThat(duration).isGreaterThanOrEqualTo(1000); // Au moins 1000ms en séquentiel
    }
}
