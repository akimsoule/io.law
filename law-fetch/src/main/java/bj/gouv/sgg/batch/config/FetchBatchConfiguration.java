package bj.gouv.sgg.batch.config;

import bj.gouv.sgg.batch.processor.FetchCurrentProcessor;
import bj.gouv.sgg.batch.processor.FetchPreviousProcessor;
import bj.gouv.sgg.batch.reader.FetchCurrentReader;
import bj.gouv.sgg.batch.reader.FetchPreviousReader;
import bj.gouv.sgg.batch.writer.FetchDocumentWriter;
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
 * Configuration Spring Batch pour les jobs de fetch.
 * Deux jobs distincts: fetchCurrent (année courante) et fetchPrevious (années précédentes).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FetchBatchConfiguration {
    
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final FetchCurrentProcessor fetchCurrentProcessor;
    private final FetchPreviousProcessor fetchPreviousProcessor;
    private final FetchDocumentWriter fetchDocumentWriter;
    
    
    /**
     * Job pour fetch des documents de l'année courante.
     */
    @Bean
    public Job fetchCurrentJob(Step fetchCurrentStep) {
        return new JobBuilder("fetchCurrentJob", jobRepository)
                .start(fetchCurrentStep)
                .build();
    }
    
    /**
     * Step pour fetch current.
     * Chunk size = 10 : traite 10 documents à la fois.
     * Utilise FetchCurrentProcessor qui ne persiste PAS les NOT_FOUND.
     * Mode mono-thread : pas de parallélisme pour éviter conflits transactionnels.
     */
    @Bean
    public Step fetchCurrentStep(FetchCurrentReader fetchCurrentReader) {
        return new StepBuilder("fetchCurrentStep", jobRepository)
                .<String, LawDocumentEntity>chunk(10, transactionManager)
                .reader(fetchCurrentReader)
                .processor(fetchCurrentProcessor)
                .writer(fetchDocumentWriter)
                .build();
    }
    
    /**
     * Job pour fetch des documents des années précédentes.
     * Utilise un cursor pour reprendre là où le scan s'est arrêté.
     */
    @Bean
    public Job fetchPreviousJob(Step fetchPreviousStep) {
        return new JobBuilder("fetchPreviousJob", jobRepository)
                .start(fetchPreviousStep)
                .build();
    }
    
    /**
     * Step pour fetch previous.
     * Configuration simple sans multithreading.
     * Chunk size = 10 : traite 10 documents à la fois.
     */
    @Bean
    public Step fetchPreviousStep(FetchPreviousReader fetchPreviousReader) {
        return new StepBuilder("fetchPreviousStep", jobRepository)
                .<String, LawDocumentEntity>chunk(10, transactionManager)
                .reader(fetchPreviousReader)
                .processor(fetchPreviousProcessor)
                .writer(fetchDocumentWriter)
                .build();
    }
}
