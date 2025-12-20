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
