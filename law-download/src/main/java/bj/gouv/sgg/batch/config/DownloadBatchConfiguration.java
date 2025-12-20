package bj.gouv.sgg.batch.config;

import bj.gouv.sgg.batch.processor.DownloadProcessor;
import bj.gouv.sgg.batch.reader.DownloadReader;
import bj.gouv.sgg.batch.writer.DownloadWriter;
import bj.gouv.sgg.entity.LawDocumentEntity;
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

/**
 * Configuration Spring Batch pour le téléchargement des PDFs.
 * Mode mono-thread par défaut.
 */
@Slf4j
@Configuration
public class DownloadBatchConfiguration {
    
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    

    
    public DownloadBatchConfiguration(JobRepository jobRepository, 
                                     PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }
    

    
    /**
     * Job de téléchargement des PDFs.
     */
    @Bean
    public Job downloadJob(Step downloadStep) {
        return new JobBuilder("downloadJob", jobRepository)
                .start(downloadStep)
                .build();
    }
    
    /**
     * Step de téléchargement séquentiel.
     * Chunk size = 10 : traite 10 documents à la fois.
     */
    @Bean
    public Step downloadStep(DownloadReader downloadReader, 
                            DownloadProcessor downloadProcessor,
                            DownloadWriter downloadWriter) {
        return new StepBuilder("downloadStep", jobRepository)
                .<LawDocumentEntity, LawDocumentEntity>chunk(10, transactionManager)
                .reader(downloadReader)
                .processor(downloadProcessor)
                .writer(downloadWriter)
                .build();
    }
}
