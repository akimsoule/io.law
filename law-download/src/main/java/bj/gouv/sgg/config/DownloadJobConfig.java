package bj.gouv.sgg.config;

import bj.gouv.sgg.batch.processor.DownloadProcessor;
import bj.gouv.sgg.batch.reader.FetchedDocumentReader;
import bj.gouv.sgg.batch.writer.FileDownloadWriter;
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

/**
 * Configuration du job Download (téléchargement des PDFs)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DownloadJobConfig {
    
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final LawProperties properties;
    
    /**
     * TaskExecutor pour traitement multi-threads.
     * Le nombre de threads est déterminé par min(max-threads, availableProcessors - 1).
     */
    @Bean(name = "downloadTaskExecutor")
    public TaskExecutor downloadTaskExecutor() {
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
        executor.setThreadNamePrefix("download-thread-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        log.info("🧵 Download TaskExecutor initialized with {} threads (configured max-threads: {}, available processors: {})",
                threadPoolSize, configuredMaxThreads, availableProcessors);
        
        return executor;
    }
    
    @Bean
    public Job downloadJob(Step downloadStep) {
        return new JobBuilder("downloadJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(downloadStep)
            .build();
    }
    
    @Bean
    public Step downloadStep(FetchedDocumentReader reader,
                             DownloadProcessor downloadProcessor,
                             FileDownloadWriter writer,
                             TaskExecutor downloadTaskExecutor) {
        
        return new StepBuilder("downloadStep", jobRepository)
            .<LawDocument, LawDocument>chunk(properties.getBatch().getChunkSize(), transactionManager)
            .reader(reader)
            .processor(downloadProcessor)
            .writer(writer) // Sauvegarde dans download_results
            .taskExecutor(downloadTaskExecutor) // ✅ Traitement multi-threads
            .faultTolerant()
            .skip(Exception.class)
            .skipLimit(Integer.MAX_VALUE)
            .listener(new org.springframework.batch.core.StepExecutionListener() {
                @Override
                public void beforeStep(org.springframework.batch.core.StepExecution stepExecution) {
                    // Réinitialiser le reader avant chaque exécution
                    reader.reset();
                    
                    // Lire les paramètres --doc ou --documentId (équivalents), --force et --maxDocuments depuis JobParameters
                    String type = stepExecution.getJobParameters().getString("type");
                    if (type != null && !type.isEmpty()) {
                        reader.setTypeFilter(type);
                        log.info("🎯 Type filter (download): {}", type);
                    }
                    String doc = stepExecution.getJobParameters().getString("doc");
                    String documentId = stepExecution.getJobParameters().getString("documentId");
                    String force = stepExecution.getJobParameters().getString("force");
                    String maxDocs = stepExecution.getJobParameters().getString("maxDocuments");
                    
                    // Accepter --doc ou --documentId comme équivalents
                    String targetDoc = (doc != null && !doc.isEmpty()) ? doc : documentId;
                    if (targetDoc != null && !targetDoc.isEmpty()) {
                        reader.setTargetDocumentId(targetDoc);
                        log.info("📄 Target document: {}", targetDoc);
                    }
                    
                    if ("true".equalsIgnoreCase(force)) {
                        reader.setForceMode(true);
                        downloadProcessor.setForceMode(true);
                        writer.setForceMode(true); // ✅ Passer aussi au writer
                        log.info("🔄 Force mode enabled");
                    }
                    
                    if (maxDocs != null && !maxDocs.isEmpty()) {
                        try {
                            reader.setMaxDocuments(Integer.parseInt(maxDocs));
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
