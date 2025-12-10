package bj.gouv.sgg.fix.config;

import bj.gouv.sgg.fix.batch.AllDocumentsReader;
import bj.gouv.sgg.fix.batch.FixProcessor;
import bj.gouv.sgg.fix.batch.FixWriter;
import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration du job de correction automatique.
 * 
 * Ce job :
 * 1. Analyse TOUS les documents (tous statuts)
 * 2. Détecte les problèmes (statut, fichiers, qualité)
 * 3. Applique les corrections automatiques
 * 4. Prépare les documents pour re-traitement
 * 
 * Exécution recommandée : Quotidienne ou après chaque batch de jobs
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class FixJobConfiguration {
    
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    
    private final AllDocumentsReader allDocumentsReader;
    private final FixProcessor fixProcessor;
    private final FixWriter fixWriter;
    
    @Bean
    public Job fixJob() {
        return new JobBuilder("fixJob", jobRepository)
            .start(fixStep())
            .build();
    }
    
    @Bean
    public Step fixStep() {
        return new StepBuilder("fixStep", jobRepository)
            .<LawDocument, LawDocument>chunk(10, transactionManager)
            .reader(allDocumentsReader)
            .processor(fixProcessor)
            .writer(fixWriter)
            .build();
    }
}
