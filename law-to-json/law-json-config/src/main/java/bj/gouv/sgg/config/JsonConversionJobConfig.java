package bj.gouv.sgg.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration orchestrateur pour la conversion PDF → JSON.
 * 
 * Fournit le job de pipeline complet : jsonConversionJob (PDF → OCR → JSON)
 * 
 * Les jobs individuels sont disponibles via les modules :
 * - ocrJob (law-pdf-ocr) : PDF → OCR uniquement
 * - ocrJsonJob (law-ocr-json) : OCR → JSON uniquement
 * 
 * Modules futurs :
 * - law-ai : Parsing IA pour enrichissement
 * - law-qa : Validation qualité des données
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class JsonConversionJobConfig {

    /**
     * Job orchestrateur PDF → JSON (pipeline complet).
     * 
     * Enchaîne les étapes :
     * 1. ocrStep (law-pdf-ocr) : Extraction OCR depuis PDFs
     * 2. ocrJsonStep (law-ocr-json) : Conversion OCR → JSON structuré
     * 
     * @param jobRepository Repository Spring Batch
     * @param ocrStep Step d'extraction OCR (défini dans law-pdf-ocr)
     * @param ocrJsonStep Step de conversion JSON (défini dans law-ocr-json)
     * @return Job configuré
     */
    @Bean
    public Job jsonConversionJob(
            JobRepository jobRepository,
            Step ocrStep,
            Step ocrJsonStep) {
        
        log.info("Configuration du job jsonConversionJob (Pipeline complet PDF → OCR → JSON)");
        
        return new JobBuilder("jsonConversionJob", jobRepository)
                .start(ocrStep)
                .next(ocrJsonStep)
                .build();
    }
}
