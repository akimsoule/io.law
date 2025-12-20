package bj.gouv.sgg.config;

import bj.gouv.sgg.batch.ConsolidateProcessor;
import bj.gouv.sgg.batch.ConsolidateReader;
import bj.gouv.sgg.batch.ConsolidateWriter;
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
 * Configuration Spring Batch pour job consolidation.
 * 
 * Workflow:
 * ConsolidateReader (EXTRACTED) → ConsolidateProcessor (verify JSON) → ConsolidateWriter (CONSOLIDATED)
 * 
 * Adaptive threading:
 * - Auto mode (0): min(CPU-1, 8)
 * - Manual mode: min(configured, CPU-1)
 * - Minimum: 1 thread
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ConsolidateBatchConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ConsolidateReader consolidateReader;
    private final ConsolidateProcessor consolidateProcessor;
    private final ConsolidateWriter consolidateWriter;

    @Value("${batch.consolidate.thread-pool-size:0}")
    private int configuredThreadPoolSize;

    /**
     * TaskExecutor avec threading adaptatif basé sur le CPU.
     */
    @Bean(name = "consolidateTaskExecutor")
    public TaskExecutor consolidateTaskExecutor() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int maxUsableProcessors = Math.max(availableProcessors - 1, 1);

        int threadPoolSize;
        if (configuredThreadPoolSize > 0) {
            // Mode manuel: utilise configuration mais ne dépasse pas CPU disponibles
            threadPoolSize = Math.min(configuredThreadPoolSize, maxUsableProcessors);
            log.info("🔧 Thread pool size: {} (manual, capped at {} CPUs)", threadPoolSize, maxUsableProcessors);
        } else {
            // Mode auto: CPU-1, max 8
            threadPoolSize = Math.min(maxUsableProcessors, 8);
            log.info("🔧 Thread pool size: {} (auto mode, {} CPUs available)", threadPoolSize, availableProcessors);
        }

        // Garantir minimum 1 thread
        threadPoolSize = Math.max(threadPoolSize, 1);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolSize);
        executor.setThreadNamePrefix("consolidate-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60); // Consolidation rapide
        executor.initialize();

        log.info("✅ ConsolidateTaskExecutor configuré: {} threads", threadPoolSize);
        return executor;
    }

    /**
     * Job Spring Batch pour consolidation.
     */
    @Bean
    public Job consolidateJob(Step consolidateStep) {
        return new JobBuilder("consolidateJob", jobRepository)
                .start(consolidateStep)
                .build();
    }

    /**
     * Step avec reader, processor, writer séquentiel.
     */
    @Bean
    public Step consolidateStep(ConsolidateReader consolidateReader) {
        return new StepBuilder("consolidateStep", jobRepository)
                .<LawDocumentEntity, LawDocumentEntity>chunk(10, transactionManager)
                .reader(consolidateReader)
                .processor(consolidateProcessor)
                .writer(consolidateWriter)
                .build();
    }
}
