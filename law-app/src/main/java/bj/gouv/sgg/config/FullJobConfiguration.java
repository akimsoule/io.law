package bj.gouv.sgg.config;

import bj.gouv.sgg.exception.LawProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration du job fullJob - Pipeline complet pour un document spécifique.
 * 
 * Ce job réutilise directement les STEPS des autres jobs (pas de sub-job execution).
 * Architecture : 5 steps séquentiels
 *   1. validateDocumentParameterStep → Valide --doc obligatoire
 *   2. fetchCurrentStep              → Fetch métadonnées (de FetchJobConfiguration)
 *   3. downloadStep                  → Download PDF (de DownloadJobConfiguration)
 *   4. pdfToJsonStep                 → Extract contenu (de PdfToJsonJobConfiguration)
 *   5. consolidateStep               → Consolidate BD (de ConsolidateJobConfiguration)
 * 
 * Usage obligatoire :
 *   java -jar law-app.jar --job=fullJob --doc=loi-2024-15 [--force=true]
 * 
 * Paramètres :
 *   --doc   : OBLIGATOIRE - ID du document (ex: loi-2024-15)
 *   --force : OPTIONNEL - true pour forcer le retraitement (défaut: false)
 * 
 * Sans --doc, le job échoue immédiatement.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FullJobConfiguration {
    
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    
    private static final String PARAM_DOC = "doc";
    private static final String PARAM_DOCUMENT_ID = "documentId";
    private static final String PARAM_FORCE = "force";
    
    /**
     * Job fullJob - Pipeline complet pour un document.
     * 
     * Enchaîne les 5 steps dans l'ordre :
     *   validate → fetch → download → extract → consolidate
     */
    @Bean
    public Job fullJob(
            @Qualifier("fetchCurrentStep") Step fetchCurrentStep,
            @Qualifier("downloadStep") Step downloadStep,
            @Qualifier("pdfToJsonStep") Step pdfToJsonStep,
            @Qualifier("consolidateStep") Step consolidateStep
    ) {
        log.info("🔧 Configuration fullJob - Réutilisation des Steps existants");
        
        return new JobBuilder("fullJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(validateDocumentParameterStep())
                .next(fetchCurrentStep)
                .next(downloadStep)
                .next(pdfToJsonStep)
                .next(consolidateStep)
                .build();
    }
    
    /**
     * Step 1/5 : Validation du paramètre --doc (obligatoire).
     * 
     * Échoue immédiatement si --doc absent.
     */
    @Bean
    public Step validateDocumentParameterStep() {
        return new StepBuilder("validateDocumentParameterStep", jobRepository)
                .tasklet(validateDocumentParameterTasklet(), transactionManager)
                .build();
    }
    
    /**
     * Tasklet de validation : vérifie que --doc est fourni.
     */
    @Bean
    public Tasklet validateDocumentParameterTasklet() {
        return (StepContribution contribution, ChunkContext chunkContext) -> {
            JobParameters params = chunkContext.getStepContext()
                    .getStepExecution()
                    .getJobParameters();
            
            String doc = params.getString(PARAM_DOC);
            String documentId = params.getString(PARAM_DOCUMENT_ID);
            String force = params.getString(PARAM_FORCE);
            
            // Accepte --doc ou --documentId
            String targetDoc = (doc != null && !doc.trim().isEmpty()) ? doc : documentId;
            
            if (targetDoc == null || targetDoc.trim().isEmpty()) {
                log.error("❌ Paramètre --doc manquant pour fullJob");
                log.error("❌ Usage: java -jar law-app.jar --job=fullJob --doc=loi-2024-15 [--force=true]");
                throw new LawProcessingException("Paramètre --doc obligatoire pour fullJob");
            }
            
            boolean forceMode = "true".equalsIgnoreCase(force);
            
            log.info("✅ Document cible validé: {}", targetDoc);
            if (forceMode) {
                log.info("⚠️  Mode FORCE activé - Retraitement complet du document");
            }
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("🚀 DÉMARRAGE PIPELINE COMPLET pour {} {}", targetDoc, forceMode ? "(FORCE)" : "");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            return RepeatStatus.FINISHED;
        };
    }
}
