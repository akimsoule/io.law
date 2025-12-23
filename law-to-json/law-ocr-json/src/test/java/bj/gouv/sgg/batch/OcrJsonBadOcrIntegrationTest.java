package bj.gouv.sgg.batch;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OcrJsonBadOcrIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job ocrJsonJob;

    @Autowired
    private LawDocumentRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void givenBadOcrFileWhenProcessingThenStatusIsExtractedOrFailed() throws Exception {
        File bad = new ClassPathResource("bad_ocr/loi-2021-13.txt").getFile();

        LawDocumentEntity doc = LawDocumentEntity.builder()
                .type("loi")
                .year(2021)
                .number("13")
                .documentId("loi-2021-13")
                .status(ProcessingStatus.OCRED)
                .ocrPath(bad.getAbsolutePath())
                .build();
        repository.saveAndFlush(doc);

        JobExecution execution = jobLauncher.run(ocrJsonJob,
                new JobParametersBuilder()
                        .addString("documentId", "loi-2021-13")
                        .addLong("timestamp", System.currentTimeMillis())
                        .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        LawDocumentEntity updated = repository.findByTypeAndYearAndNumber("loi", 2021, "13").orElseThrow();
        assertThat(updated.getStatus()).isIn(ProcessingStatus.EXTRACTED, ProcessingStatus.FAILED_EXTRACTION);
        // If extracted, jsonPath should be present
        if (updated.getStatus() == ProcessingStatus.EXTRACTED) {
            assertThat(updated.getJsonPath()).isNotNull();
        }
    }
}
