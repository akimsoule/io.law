package bj.gouv.sgg.batch.config;

import bj.gouv.sgg.batch.processor.OcrJsonProcessor;
import bj.gouv.sgg.batch.reader.OcrJsonReader;
import bj.gouv.sgg.batch.writer.OcrJsonWriter;
import bj.gouv.sgg.entity.LawDocumentEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration Spring Batch pour l'extraction JSON depuis OCR.
 * Mode mono-thread par défaut.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class OcrBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final OcrJsonReader ocrJsonReader;
    private final OcrJsonProcessor ocrJsonProcessor;
    private final OcrJsonWriter ocrJsonWriter;



    @Bean
    public Job ocrJsonJob(Step ocrJsonStep) {
        return new JobBuilder("ocrJsonJob", jobRepository)
                .start(ocrJsonStep)
                .build();
    }

    @Bean
    public Step ocrJsonStep(OcrJsonReader ocrJsonReader) {
        return new StepBuilder("ocrJsonStep", jobRepository)
                .<LawDocumentEntity, LawDocumentEntity>chunk(10, transactionManager)
                .reader(ocrJsonReader)
                .processor(ocrJsonProcessor)
                .writer(ocrJsonWriter)
                .build();
    }
}
