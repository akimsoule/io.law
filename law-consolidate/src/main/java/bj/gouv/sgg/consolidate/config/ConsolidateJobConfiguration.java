package bj.gouv.sgg.consolidate.config;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.consolidate.batch.processor.ConsolidationProcessor;
import bj.gouv.sgg.consolidate.batch.reader.JsonFileItemReader;
import bj.gouv.sgg.consolidate.batch.writer.ConsolidationWriter;
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
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration Spring Batch pour le job de consolidation.
 * 
 * <p><b>Job</b> : {@code consolidateJob}
 * <ul>
 *   <li>Lit les documents EXTRACTED depuis {@code law_documents}</li>
 *   <li>Parse le JSON généré par law-ocr-json</li>
 *   <li>Insère articles, métadonnées, signataires en BD MySQL</li>
 *   <li>Met à jour status → CONSOLIDATED</li>
 * </ul>
 * 
 * <p><b>Pipeline</b> :
 * <pre>
 * Reader (JsonFileItemReader)
 *   ↓ LawDocument (status=EXTRACTED)
 * Processor (ConsolidationProcessor)
 *   ↓ Parse JSON → Consolide BD → LawDocument (status=CONSOLIDATED)
 * Writer (ConsolidationWriter)
 *   ↓ Update status en BD
 * </pre>
 * 
 * <p><b>Configuration</b> :
 * <ul>
 *   <li>Chunk size : 10 documents (configurable via {@link LawProperties})</li>
 *   <li>Transaction : Par chunk (rollback si erreur)</li>
 *   <li>Idempotent : Re-run safe (UPDATE si existe)</li>
 * </ul>
 * 
 * <p><b>Commande CLI</b> :
 * <pre>
 * java -jar law-app.jar --job=consolidateJob
 * </pre>
 * 
 * @see JsonFileItemReader
 * @see ConsolidationProcessor
 * @see ConsolidationWriter
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ConsolidateJobConfiguration {
    
    private final JobRepository jobRepository;
    private final LawProperties properties;
    private final PlatformTransactionManager transactionManager;
    
    /**
     * Job Spring Batch pour consolider les documents extraits.
     * 
     * @param consolidateStep Step de consolidation
     * @return Job configuré
     */
    @Bean
    public Job consolidateJob(Step consolidateStep) {
        return new JobBuilder("consolidateJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(consolidateStep)
            .build();
    }
    
    /**
     * Step de consolidation : Reader → Processor → Writer.
     * 
     * @param reader Lit documents EXTRACTED
     * @param processor Consolide JSON → BD
     * @param writer Sauvegarde status
     * @return Step configuré
     */
    @Bean
    public Step consolidateStep(JsonFileItemReader reader,
                                ConsolidationProcessor processor,
                                ConsolidationWriter writer) {
        int chunkSize = properties.getBatch().getChunkSize();
        log.info("⚙️ ConsolidateStep configuré: chunk size = {}", chunkSize);
        
        return new StepBuilder("consolidateStep", jobRepository)
            .<LawDocument, LawDocument>chunk(chunkSize, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skip(Exception.class)
            .skipLimit(Integer.MAX_VALUE)
            .listener(new org.springframework.batch.core.StepExecutionListener() {
                @Override
                public void beforeStep(org.springframework.batch.core.StepExecution stepExecution) {
                    String type = stepExecution.getJobParameters().getString("type");
                    if (type != null && !type.isEmpty()) {
                        reader.setTypeFilter(type);
                        log.info("🎯 Type filter (consolidate): {}", type);
                    }
                }
            })
            .build();
    }
}
