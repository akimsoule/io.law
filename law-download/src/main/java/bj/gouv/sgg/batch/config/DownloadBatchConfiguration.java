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
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration Spring Batch pour le téléchargement des PDFs.
 * Parallélise les téléchargements avec TaskExecutor multi-threadé.
 * S'adapte automatiquement aux capacités de la machine (CPU-1, plafonné à 8).
 */
@Slf4j
@Configuration
public class DownloadBatchConfiguration {
    
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    
    @Value("${batch.download.thread-pool-size:0}")
    private int configuredThreadPoolSize;
    
    public DownloadBatchConfiguration(JobRepository jobRepository, 
                                     PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }
    
    /**
     * TaskExecutor pour paralléliser les téléchargements.
     * Adapte automatiquement le nombre de threads :
     * - Réserve 1 CPU pour le système (CPU-1)
     * - Mode auto : min(CPU-1, 8) si configured=0
     * - Mode configuré : min(configured, CPU-1) si configured>0
     * - Garantit minimum 1 thread
     */
    @Bean(name = "downloadTaskExecutor")
    public TaskExecutor downloadTaskExecutor() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int maxUsableProcessors = Math.max(availableProcessors - 1, 1);
        
        int threadPoolSize;
        if (configuredThreadPoolSize > 0) {
            threadPoolSize = Math.min(configuredThreadPoolSize, maxUsableProcessors);
        } else {
            threadPoolSize = Math.min(maxUsableProcessors, 8);
        }
        
        threadPoolSize = Math.max(threadPoolSize, 1);
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolSize);
        executor.setQueueCapacity(threadPoolSize * 2);
        executor.setThreadNamePrefix("download-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120); // 2 minutes pour téléchargements longs
        executor.initialize();
        
        String mode = configuredThreadPoolSize > 0 ? "configuré" : "auto";
        log.info("✅ DownloadTaskExecutor initialisé avec {} threads (CPU: {}, demandé: {}, mode: {})", 
                 threadPoolSize, availableProcessors, 
                 configuredThreadPoolSize > 0 ? configuredThreadPoolSize : "auto", mode);
        return executor;
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
