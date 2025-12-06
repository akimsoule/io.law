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
                             FileDownloadWriter writer) {
        
        // Le reader ne retourne que les documents FETCHED, donc pas besoin de FetchProcessor
        // On télécharge directement en mono-thread pour éviter les duplicates
        // Le processor télécharge le PDF et le writer le sauvegarde en base
        return new StepBuilder("downloadStep", jobRepository)
            .<LawDocument, LawDocument>chunk(1, transactionManager) // Process one document at a time
            .reader(reader)
            .processor(downloadProcessor)
            .writer(writer) // Sauvegarde dans download_results
            // Pas de taskExecutor = exécution synchrone en mono-thread
            .listener(new org.springframework.batch.core.StepExecutionListener() {
                @Override
                public void beforeStep(org.springframework.batch.core.StepExecution stepExecution) {
                    // Lire les paramètres --documentId, --force et --maxDocuments depuis JobParameters
                    String doc = stepExecution.getJobParameters().getString("documentId");
                    String force = stepExecution.getJobParameters().getString("force");
                    String maxDocs = stepExecution.getJobParameters().getString("maxDocuments");
                    
                    if (doc != null && !doc.isEmpty()) {
                        reader.setTargetDocumentId(doc);
                        log.info("📄 Target document: {}", doc);
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
