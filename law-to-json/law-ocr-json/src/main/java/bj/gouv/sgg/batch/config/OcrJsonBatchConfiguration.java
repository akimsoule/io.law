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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration Spring Batch pour l'extraction JSON depuis OCR.
 * 
 * <p>Architecture :
 * <pre>
 * OcrJsonReader (DB + FileStorage) → OcrJsonProcessor (Parser) → OcrJsonWriter (DB)
 * </pre>
 * 
 * <p>Multi-threading :
 * - CPU-aware (CPU-1, capped at 8)
 * - Configurable via batch.ocr-json.thread-pool-size
 * - Await termination : 120 secondes
 * 
 * <p>Chunk size : 10 documents par batch
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class OcrJsonBatchConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final OcrJsonReader ocrJsonReader;
    private final OcrJsonProcessor ocrJsonProcessor;
    private final OcrJsonWriter ocrJsonWriter;

    @Value("${batch.ocr-json.thread-pool-size:0}")
    private int configuredThreadPoolSize;

    /**
     * TaskExecutor multi-threadé avec gestion CPU-aware.
     * 
     * <p>Logique adaptive :
     * <ul>
     *   <li>threadPoolSize configuré > 0 : min(configuré, CPU-1)</li>
     *   <li>threadPoolSize = 0 (auto) : min(CPU-1, 8)</li>
     *   <li>Minimum garanti : 1 thread</li>
     * </ul>
     * 
     * <p>Await termination : 120 secondes (traitement JSON peut être long)
     */
    @Bean(name = "ocrJsonTaskExecutor")
    public TaskExecutor ocrJsonTaskExecutor() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int maxUsableProcessors = Math.max(availableProcessors - 1, 1); // Réserver 1 CPU

        int threadPoolSize;
        if (configuredThreadPoolSize > 0) {
            // Mode manuel : limiter aux CPUs disponibles
            threadPoolSize = Math.min(configuredThreadPoolSize, maxUsableProcessors);
            log.info("🔧 Thread pool size: {} (manual, limited by {} CPUs)", 
                    threadPoolSize, maxUsableProcessors);
        } else {
            // Mode auto : CPU-1, max 8
            threadPoolSize = Math.min(maxUsableProcessors, 8);
            log.info("🔧 Thread pool size: {} (auto mode: CPU-1, capped at 8, {} CPUs available)", 
                    threadPoolSize, availableProcessors);
        }

        // Garantir au moins 1 thread
        threadPoolSize = Math.max(threadPoolSize, 1);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolSize);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ocr-json-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120); // 2 minutes pour JSON extraction
        executor.initialize();

        return executor;
    }

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
