package bj.gouv.sgg.config;

import bj.gouv.sgg.batch.processor.CurrentYearLawDocumentProcessor;
import bj.gouv.sgg.batch.processor.PreviousYearLawDocumentProcessor;
import bj.gouv.sgg.batch.reader.CurrentYearLawDocumentReader;
import bj.gouv.sgg.batch.reader.PreviousYearLawDocumentReader;
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
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class FetchJobConfiguration {

    private static final String PARAM_DOC = "doc";
    private static final String PARAM_FORCE = "force";
    private static final String VALUE_TRUE = "true";
    
    private final JobRepository jobRepository;
    private final LawProperties properties;
    private final PlatformTransactionManager transactionManager;
    
    /**
     * TaskExecutor pour traitement multi-threads.
     * Le nombre de threads est déterminé par min(max-threads, availableProcessors - 1).
     */
    @Bean(name = "fetchTaskExecutor")
    public TaskExecutor fetchTaskExecutor() {
        // Utiliser getEffectiveMaxThreads() qui gère le cas maxThreads=0
        int configuredMaxThreads = properties.getBatch().getEffectiveMaxThreads();
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        
        // Utiliser le minimum entre max-threads configuré et (processeurs disponibles - 1)
        // Garder au moins 1 thread
        int threadPoolSize = Math.max(1, Math.min(configuredMaxThreads, availableProcessors - 1));
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolSize);
        executor.setQueueCapacity(properties.getBatch().getChunkSize() * 2);
        executor.setThreadNamePrefix("fetch-thread-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        log.info("🧵 Fetch TaskExecutor initialized with {} threads (configured max-threads: {}, available processors: {})",
                threadPoolSize, configuredMaxThreads, availableProcessors);
        
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
                                 CurrentYearLawDocumentProcessor processor,
                                 FetchWriter fetchWriter,
                                 TaskExecutor fetchTaskExecutor) {
        return new StepBuilder("fetchCurrentStep", jobRepository)
            .<LawDocument, LawDocument>chunk(properties.getBatch().getChunkSize(), transactionManager)
            .reader(currentYearReader)
            .processor(processor)
            .writer(fetchWriter)
            .faultTolerant()
            .skip(Exception.class)
            .skipLimit(Integer.MAX_VALUE)
            .taskExecutor(fetchTaskExecutor) // ✅ Traitement multi-threads
            .listener(new org.springframework.batch.core.StepExecutionListener() {
                @Override
                public void beforeStep(org.springframework.batch.core.StepExecution stepExecution) {
                    String type = stepExecution.getJobParameters().getString("type");
                    if (type != null && !type.isEmpty()) {
                        currentYearReader.setTypeFilter(type);
                        log.info("🎯 Type filter (current): {}", type);
                    }
                    // Lire les paramètres --doc ou --documentId (équivalents), --force et --maxDocuments depuis JobParameters
                    String doc = stepExecution.getJobParameters().getString(PARAM_DOC);
                    String documentId = stepExecution.getJobParameters().getString("documentId");
                    String force = stepExecution.getJobParameters().getString(PARAM_FORCE);
                    String maxDocs = stepExecution.getJobParameters().getString("maxDocuments");
                    
                    // Accepter --doc ou --documentId comme équivalents
                    String targetDoc = (doc != null && !doc.isEmpty()) ? doc : documentId;
                    if (targetDoc != null && !targetDoc.isEmpty()) {
                        currentYearReader.setTargetDocumentId(targetDoc);
                        log.info("📄 Target document: {}", targetDoc);
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
            // Note: Timeout handling retiré pour éviter deadlock dans orchestration
            // Le step peut timeout, mais le job se termine normalement
            // L'orchestrateur relancera le job au prochain cycle
            .build();
    }
    
    @Bean
    public Step fetchPreviousStep(PreviousYearLawDocumentReader previousYearsReader,
                                  PreviousYearLawDocumentProcessor processor,
                                  FetchWriter fetchWriter,
                                  TaskExecutor fetchTaskExecutor) {
        return new StepBuilder("fetchPreviousStep", jobRepository)
            .<LawDocument, LawDocument>chunk(properties.getBatch().getChunkSize(), transactionManager)
            .reader(previousYearsReader)
            .processor(processor)
            .writer(fetchWriter)
            .faultTolerant()
            .skip(Exception.class)
            .skipLimit(Integer.MAX_VALUE)
            .taskExecutor(fetchTaskExecutor) // ✅ Traitement multi-threads
            .listener(new org.springframework.batch.core.StepExecutionListener() {
                @Override
                public void beforeStep(org.springframework.batch.core.StepExecution stepExecution) {
                    String type = stepExecution.getJobParameters().getString("type");
                    if (type != null && !type.isEmpty()) {
                        previousYearsReader.setTypeFilter(type);
                        log.info("🎯 Type filter (previous): {}", type);
                    }
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
                    
                    // ✅ Activer consolidation NOT_FOUND pour PreviousYears
                    fetchWriter.setEnableNotFoundConsolidation(true);
                    
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
