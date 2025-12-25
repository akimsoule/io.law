package bj.gouv.sgg.batch.config;

import bj.gouv.sgg.batch.processor.PdfImgProcessor;
import bj.gouv.sgg.batch.reader.PdfImgReader;
import bj.gouv.sgg.batch.writer.PdfImgWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class PdfImgBatchConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PdfImgProcessor processor;
    private final PdfImgWriter writer;
    private final PdfImgReader reader;

    @Value("${law.chunkSize:10}")
    private int chunkSize;

    @Bean
    public Job pdfToImagesJob(Step pdfToImagesStep) {
        return new JobBuilder("pdfToImagesJob", jobRepository)
                .start(pdfToImagesStep)
                .build();
    }

    @Bean
    public Step pdfToImagesStep() {
        return new StepBuilder("pdfToImagesStep", jobRepository)
                .<bj.gouv.sgg.entity.LawDocumentEntity, bj.gouv.sgg.entity.LawDocumentEntity>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
