package bj.gouv.sgg.config;

import bj.gouv.sgg.batch.processor.FetchProcessor;
import bj.gouv.sgg.batch.reader.CurrentYearLawDocumentReader;
import bj.gouv.sgg.batch.reader.PreviousYearsLawDocumentReader;
import bj.gouv.sgg.batch.writer.FetchWriter;
import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class FetchJobConfiguration {

    private static final String TIMEOUT_EXIT_STATUS = "TIMEOUT";
    private static final String PARAM_DOC = "doc";
    private static final String PARAM_FORCE = "force";
    private static final String VALUE_TRUE = "true";
    
    private final JobRepository jobRepository;
    private final LawProperties properties;
    private final PlatformTransactionManager transactionManager;
    
    @Bean
    public TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        int effectiveThreads = properties.getBatch().getEffectiveMaxThreads();
        executor.setConcurrencyLimit(effectiveThreads);
        log.info("⚙️ TaskExecutor configured with {} threads", effectiveThreads);
        return executor;
    }
    
    // ========================================================================
    // FETCH CURRENT YEAR JOB - Scan complet de l'année en cours
    // ========================================================================
    
    @Bean
    public Job fetchCurrentJob(Step fetchCurrentStep) {
        return new JobBuilder("fetchCurrentJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(fetchCurrentStep)
            .build();
    }
    
    @Bean
    public Step fetchCurrentStep(CurrentYearLawDocumentReader currentYearReader, 
                                 FetchProcessor processor,
                                 FetchWriter fetchWriter) {
        return new StepBuilder("fetchCurrentStep", jobRepository)
            .<LawDocument, LawDocument>chunk(properties.getBatch().getChunkSize(), transactionManager)
            .reader(currentYearReader)
            .processor(processor)
            .writer(fetchWriter)
            .taskExecutor(taskExecutor())
            .listener(new org.springframework.batch.core.StepExecutionListener() {
                @Override
                public void beforeStep(org.springframework.batch.core.StepExecution stepExecution) {
                    // Lire les paramètres --doc, --force et --maxDocuments depuis JobParameters
                    String doc = stepExecution.getJobParameters().getString(PARAM_DOC);
                    String force = stepExecution.getJobParameters().getString(PARAM_FORCE);
                    String maxDocs = stepExecution.getJobParameters().getString("maxDocuments");
                    
                    if (doc != null && !doc.isEmpty()) {
                        currentYearReader.setTargetDocumentId(doc);
                        log.info("📄 Target document: {}", doc);
                    }
                    
                    if (VALUE_TRUE.equalsIgnoreCase(force)) {
                        currentYearReader.setForceMode(true);
                        fetchWriter.setForceMode(true);
                        log.info("🔄 Force mode enabled");
                    }
                    
                    if (maxDocs != null && !maxDocs.isEmpty()) {
                        try {
                            currentYearReader.setMaxDocuments(Integer.parseInt(maxDocs));
                            log.info("📊 Max documents: {}", maxDocs);
                        } catch (NumberFormatException e) {
                            log.warn("⚠️ Invalid maxDocuments value: {}", maxDocs);
                        }
                    }
                }
            })
            .build();
    }
    
    // ========================================================================
    // FETCH PREVIOUS YEARS JOB - Scan optimisé avec cache (1960 à année-1)
    // ========================================================================
    
    @Bean
    public Job fetchPreviousJob(Step fetchPreviousStep) {
        return new JobBuilder("fetchPreviousJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(fetchPreviousStep)
                .on(TIMEOUT_EXIT_STATUS).stopAndRestart(fetchPreviousStep)
                .from(fetchPreviousStep).on("*").end()
            .end()
            .build();
    }
    
    @Bean
    public Step fetchPreviousStep(PreviousYearsLawDocumentReader previousYearsReader, 
                                  FetchProcessor processor,
                                  FetchWriter fetchWriter) {
        return new StepBuilder("fetchPreviousStep", jobRepository)
            .<LawDocument, LawDocument>chunk(properties.getBatch().getChunkSize(), transactionManager)
            .reader(previousYearsReader)
            .processor(processor)
            .writer(fetchWriter)
            .taskExecutor(taskExecutor())
            .listener(new org.springframework.batch.core.StepExecutionListener() {
                @Override
                public void beforeStep(org.springframework.batch.core.StepExecution stepExecution) {
                    // Lire les paramètres --doc, --force et --maxDocuments depuis JobParameters
                    String doc = stepExecution.getJobParameters().getString(PARAM_DOC);
                    String force = stepExecution.getJobParameters().getString(PARAM_FORCE);
                    String maxDocs = stepExecution.getJobParameters().getString("maxDocuments");
                    
                    if (doc != null && !doc.isEmpty()) {
                        previousYearsReader.setTargetDocumentId(doc);
                        log.info("📄 Target document: {}", doc);
                    }
                    
                    if (VALUE_TRUE.equalsIgnoreCase(force)) {
                        previousYearsReader.setForceMode(true);
                        fetchWriter.setForceMode(true);
                        log.info("🔄 Force mode enabled");
                    }
                    
                    if (maxDocs != null && !maxDocs.isEmpty()) {
                        try {
                            previousYearsReader.setMaxDocuments(Integer.parseInt(maxDocs));
                            log.info("📊 Max documents: {}", maxDocs);
                        } catch (NumberFormatException e) {
                            log.warn("⚠️ Invalid maxDocuments value: {}", maxDocs);
                        }
                    }
                }
            })
            .build();
    }
}
