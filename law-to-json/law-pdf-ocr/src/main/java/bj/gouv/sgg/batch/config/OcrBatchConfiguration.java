package bj.gouv.sgg.batch.config;

import bj.gouv.sgg.batch.processor.OcrProcessor;
import bj.gouv.sgg.batch.reader.OcrReader;
import bj.gouv.sgg.batch.writer.OcrWriter;
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
 * Configuration Spring Batch pour l'extraction OCR des PDFs.
 * Parallélise le traitement OCR avec TaskExecutor multi-threadé.
 * S'adapte automatiquement aux capacités de la machine (CPU-1, plafonné à 8).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OcrBatchConfiguration {
    
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final OcrProcessor ocrProcessor;
    private final OcrWriter ocrWriter;
    
    @Value("${batch.ocr.thread-pool-size:0}")
    private int configuredThreadPoolSize;
    
    /**
     * TaskExecutor pour paralléliser le traitement OCR.
     * OCR est CPU-intensif, donc on utilise CPU-1 pour ne pas bloquer le système.
     * 
     * Logique adaptive:
     * - Si configuredThreadPoolSize > 0: utilise min(configured, CPU-1)
     * - Si configuredThreadPoolSize = 0 (auto): utilise min(CPU-1, 8)
     * - Garantit minimum 1 thread
     * 
     * Exemple Raspberry Pi 4 CPU (4 cores):
     * - config=0 → 3 threads (min(4-1, 8))
     * - config=10 → 3 threads (min(10, 4-1))
     */
    @Bean(name = "ocrTaskExecutor")
    public TaskExecutor ocrTaskExecutor() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int maxUsableProcessors = Math.max(availableProcessors - 1, 1); // Réserver 1 CPU
        
        int threadPoolSize;
        if (configuredThreadPoolSize > 0) {
            // Mode manuel: respecter config mais limiter aux CPUs disponibles
            threadPoolSize = Math.min(configuredThreadPoolSize, maxUsableProcessors);
            log.info("🎛️  Mode manuel: thread-pool-size configuré à {}, limité à {} (CPU disponibles)", 
                     configuredThreadPoolSize, threadPoolSize);
        } else {
            // Mode auto: utiliser CPUs disponibles mais plafonner à 8
            threadPoolSize = Math.min(maxUsableProcessors, 8);
            log.info("🤖 Mode auto: détection {} CPUs, utilisation de {} threads", 
                     availableProcessors, threadPoolSize);
        }
        
        // Garantir minimum 1 thread
        threadPoolSize = Math.max(threadPoolSize, 1);
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolSize);
        executor.setQueueCapacity(threadPoolSize * 2);
        executor.setThreadNamePrefix("ocr-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(180); // 3 minutes pour OCR long
        executor.initialize();
        
        log.info("✅ OcrTaskExecutor initialisé avec {} threads (CPU: {}, demandé: {}, mode: {})", 
                 threadPoolSize, 
                 availableProcessors,
                 configuredThreadPoolSize > 0 ? configuredThreadPoolSize : "auto",
                 configuredThreadPoolSize > 0 ? "configuré" : "auto");
        
        return executor;
    }
    
    /**
     * Calcule le nombre effectif de threads.
     * Utilisé pour la configuration du job.
     */
    private int getEffectiveThreadPoolSize() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int maxUsableProcessors = Math.max(availableProcessors - 1, 1);
        
        if (configuredThreadPoolSize > 0) {
            return Math.max(Math.min(configuredThreadPoolSize, maxUsableProcessors), 1);
        } else {
            return Math.max(Math.min(maxUsableProcessors, 8), 1);
        }
    }
    
    /**
     * Job d'extraction OCR.
     * Lit les documents DOWNLOADED, effectue l'OCR, sauvegarde les fichiers texte.
     */
    @Bean
    public Job ocrJob(Step ocrStep) {
        return new JobBuilder("ocrJob", jobRepository)
                .start(ocrStep)
                .build();
    }
    
    /**
     * Step d'extraction OCR séquentiel.
     * Chunk size = 10 : traite 10 documents à la fois.
     */
    @Bean
    public Step ocrStep(OcrReader ocrReader) {
        return new StepBuilder("ocrStep", jobRepository)
                .<LawDocumentEntity, LawDocumentEntity>chunk(10, transactionManager)
                .reader(ocrReader)
                .processor(ocrProcessor)
                .writer(ocrWriter)
                .build();
    }
}
