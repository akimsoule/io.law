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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
    
    @Value("${batch.fetch.thread-pool-size:0}")
    private int configuredThreadPoolSize;
    
    /**
     * TaskExecutor pour paralléliser le traitement des chunks.
     * Adapte automatiquement le nombre de threads aux capacités de la machine :
     * - Utilise min(configured, availableProcessors) pour ne jamais surcharger
     * - Si configured=0 : auto = min(availableProcessors, 8)
     * - Minimum de 1 thread dans tous les cas
     * 
     * Exemples :
     * - Raspberry Pi (4 CPU) + configured=10 → utilise 4 threads
     * - Serveur (32 CPU) + configured=10 → utilise 10 threads
     * - Desktop (16 CPU) + configured=0 → utilise 8 threads (auto-plafonné)
     */
    @Bean(name = "fetchTaskExecutor")
    public TaskExecutor fetchTaskExecutor() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int maxUsableProcessors = Math.max(availableProcessors - 1, 1); // Réserver 1 CPU pour le système
        
        int threadPoolSize;
        if (configuredThreadPoolSize > 0) {
            // Respecter la config MAIS ne jamais dépasser les CPU disponibles (moins 1 pour le système)
            threadPoolSize = Math.min(configuredThreadPoolSize, maxUsableProcessors);
        } else {
            // Mode auto : min(CPU-1, 8) pour éviter surcharge sur gros serveurs
            threadPoolSize = Math.min(maxUsableProcessors, 8);
        }
        
        // Garantir au minimum 1 thread
        threadPoolSize = Math.max(threadPoolSize, 1);
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolSize);
        executor.setQueueCapacity(threadPoolSize * 2);
        executor.setThreadNamePrefix("fetch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        String mode = configuredThreadPoolSize > 0 ? "configuré" : "auto";
        log.info("✅ FetchTaskExecutor initialisé avec {} threads (CPU: {}, demandé: {}, mode: {})", 
                 threadPoolSize, availableProcessors, 
                 configuredThreadPoolSize > 0 ? configuredThreadPoolSize : "auto", mode);
        return executor;
    }
    
    /**
     * Retourne le nombre de threads effectivement utilisé.
     * Nécessaire pour throttleLimit dans les steps.
     */
    private int getEffectiveThreadPoolSize() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int maxUsableProcessors = Math.max(availableProcessors - 1, 1); // Réserver 1 CPU pour le système
        
        int threadPoolSize;
        if (configuredThreadPoolSize > 0) {
            threadPoolSize = Math.min(configuredThreadPoolSize, maxUsableProcessors);
        } else {
            threadPoolSize = Math.min(maxUsableProcessors, 8);
        }
        
        return Math.max(threadPoolSize, 1);
    }
    
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
     * TaskExecutor : parallélise le traitement avec N threads.
     * throttleLimit : limite le nombre de chunks concurrents (résout les race conditions du reader).
     */
    @Bean
    public Step fetchCurrentStep(FetchCurrentReader fetchCurrentReader, TaskExecutor fetchTaskExecutor) {
        return new StepBuilder("fetchCurrentStep", jobRepository)
                .<String, LawDocumentEntity>chunk(10, transactionManager)
                .reader(fetchCurrentReader)
                .processor(fetchCurrentProcessor)
                .writer(fetchDocumentWriter)
                .taskExecutor(fetchTaskExecutor)
                .throttleLimit(getEffectiveThreadPoolSize())
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
     * Chunk size = 10 : traite 10 documents à la fois.
     * Utilise FetchPreviousProcessor qui persiste FETCHED + NOT_FOUND et met à jour le cursor.
     * TaskExecutor : parallélise le traitement avec N threads.
     * throttleLimit : limite le nombre de chunks concurrents (résout les race conditions du reader et cursor).
     */
    @Bean
    public Step fetchPreviousStep(FetchPreviousReader fetchPreviousReader, TaskExecutor fetchTaskExecutor) {
        return new StepBuilder("fetchPreviousStep", jobRepository)
                .<String, LawDocumentEntity>chunk(10, transactionManager)
                .reader(fetchPreviousReader)
                .processor(fetchPreviousProcessor)
                .writer(fetchDocumentWriter)
                .taskExecutor(fetchTaskExecutor)
                .throttleLimit(getEffectiveThreadPoolSize())
                .build();
    }
}
