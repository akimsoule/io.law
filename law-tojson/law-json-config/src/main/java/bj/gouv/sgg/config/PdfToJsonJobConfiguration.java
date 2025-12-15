package bj.gouv.sgg.config;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.processor.PdfToJsonProcessor;
import bj.gouv.sgg.reader.DownloadedPdfReader;
import bj.gouv.sgg.writer.JsonResultWriter;
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
 * Configuration Spring Batch pour le job de transformation PDF → JSON.
 * 
 * <p><b>Job unique</b> : {@code pdfToJsonJob}
 * 
 * <p><b>Stratégie Fallback automatique</b> (dans PdfToJsonProcessor) :
 * <ol>
 *   <li><b>1ère tentative - Ollama</b> :
 *       <ul>
 *         <li>Conditions : {@code law.capacity.ia >= 4} (16GB+ RAM)</li>
 *         <li>Vérifications : Ollama pingable + modèle disponible</li>
 *         <li>Avantage : Gratuit, rapide, privé</li>
 *       </ul>
 *   </li>
 *   <li><b>2ème tentative - Groq API</b> (fallback) :
 *       <ul>
 *         <li>Conditions : {@code law.groq.api-key} configurée</li>
 *         <li>Vérifications : Pas de timeout, pas de 429 (rate limit)</li>
 *         <li>Limitation : Abonnement gratuit avec latence possible</li>
 *       </ul>
 *   </li>
 *   <li><b>3ème tentative - OCR</b> (fallback) :
 *       <ul>
 *         <li>Conditions : {@code law.capacity.ocr >= 2} (4GB+ RAM)</li>
 *         <li>Étapes : 
 *             <ol>
 *               <li>Extraction texte OCR via Tesseract</li>
 *               <li>Parsing articles via regex patterns</li>
 *             </ol>
 *         </li>
 *         <li>Avantage : Toujours disponible (pas de dépendance externe)</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><b>Idempotence</b> : N'écrase JSON existant que si confiance supérieure
 * 
 * <p><b>Workflow</b> :
 * <pre>
 * LawDocument (status=DOWNLOADED)
 *     ↓
 * DownloadedPdfReader (lit PDFs depuis data/pdfs/)
 *     ↓
 * PdfToJsonProcessor (stratégie fallback Ollama → Groq → OCR)
 *     ↓
 * JsonResultWriter (sauvegarde JSON dans data/articles/)
 *     ↓
 * LawDocument (status=EXTRACTED)
 * </pre>
 * 
 * @see bj.gouv.sgg.processor.PdfToJsonProcessor
 * @see bj.gouv.sgg.reader.DownloadedPdfReader
 * @see bj.gouv.sgg.writer.JsonResultWriter
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class PdfToJsonJobConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    
    private final PdfToJsonProcessor pdfToJsonProcessor;
    private final JsonResultWriter jsonResultWriter;
    
    /**
     * Job principal : Transformation PDF → JSON avec stratégie fallback.
     * 
     * <p><b>Reader</b> : {@link DownloadedPdfReader} - Documents status=DOWNLOADED
     * <p><b>Processor</b> : {@link PdfToJsonProcessor} - Fallback Ollama → Groq → OCR
     * <p><b>Writer</b> : {@link JsonResultWriter} - Fichiers .json avec métadonnées confiance
     * 
     * <p><b>Chunk size</b> : 1 (traitement PDF intensif, surtout avec IA)
     * <p><b>Fault tolerance</b> : Skip sur erreur, continue job (n'arrête pas tout)
     * 
     * @param step Step principal (injection automatique)
     * @return Job pdfToJsonJob
     */
    @Bean
    public Job pdfToJsonJob(Step pdfToJsonStep) {
        log.info("🔧 Configuration pdfToJsonJob - Stratégie fallback Ollama → Groq → OCR");
        
        return new JobBuilder("pdfToJsonJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(pdfToJsonStep)
                .build();
    }
    
    /**
     * Step unique : Transformation PDF → JSON.
     * 
     * <p><b>Chunk size</b> : 1 document à la fois (traitement IA/OCR intensif)
     * <p><b>Skip limit</b> : Illimité (continue malgré erreurs individuelles)
     * <p><b>Exceptions skippées</b> : Toutes exceptions (log + continue)
     * 
     * @param reader Reader configuré avec paramètres du job (injection automatique)
     * @return Step pdfToJsonStep
     */
    @Bean
    public Step pdfToJsonStep(DownloadedPdfReader reader) {
        return new StepBuilder("pdfToJsonStep", jobRepository)
                .<LawDocument, LawDocument>chunk(1, transactionManager)
                .reader(reader)
                .processor(pdfToJsonProcessor)
                .writer(jsonResultWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(Integer.MAX_VALUE)
                .build();
    }
    
    /**
     * Reader bean configuré avec les paramètres du job.
     * 
     * <p><b>Paramètres supportés</b> :
     * <ul>
     *   <li><b>doc ou documentId</b> : ID du document à traiter (ex: "loi-2024-15")</li>
     *   <li><b>force</b> : "true" pour forcer le re-traitement des EXTRACTED</li>
     *   <li><b>maxDocuments</b> : Nombre max de documents (défaut: 10)</li>
     * </ul>
     * 
     * @param doc Document spécifique à traiter (optionnel, alias de documentId)
     * @param documentId Document spécifique à traiter (optionnel)
     * @param force Mode force ("true" ou null)
     * @param maxDocuments Nombre max de documents (défaut: 10)
     * @param repository Repository JPA pour LawDocument
     * @return DownloadedPdfReader configuré
     */
    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public DownloadedPdfReader downloadedPdfReaderBean(
            @org.springframework.beans.factory.annotation.Value("#{jobParameters['doc']}") String doc,
            @org.springframework.beans.factory.annotation.Value("#{jobParameters['documentId']}") String documentId,
            @org.springframework.beans.factory.annotation.Value("#{jobParameters['force']}") String force,
            @org.springframework.beans.factory.annotation.Value("#{jobParameters['maxDocuments']}") String maxDocuments,
            @org.springframework.beans.factory.annotation.Value("#{jobParameters['type']}") String type,
            bj.gouv.sgg.repository.LawDocumentRepository repository
    ) {
        DownloadedPdfReader reader = new DownloadedPdfReader(repository);
        
        // Configuration document ciblé (accepter --doc ou --documentId comme équivalents)
        String targetDoc = (doc != null && !doc.isBlank()) ? doc : documentId;
        if (targetDoc != null && !targetDoc.isBlank()) {
            reader.setTargetDocumentId(targetDoc);
        }
        
        // Configuration mode force
        if ("true".equalsIgnoreCase(force)) {
            reader.setForceMode(true);
        }
        
        // Configuration maxDocuments (défaut : 10)
        if (maxDocuments != null && !maxDocuments.isBlank()) {
            try {
                reader.setMaxDocuments(Integer.parseInt(maxDocuments));
            } catch (NumberFormatException e) {
                log.warn("⚠️ Invalid maxDocuments value: {}, using default (10)", maxDocuments);
            }
        }
        // Filtre type (ex: loi)
        if (type != null && !type.isBlank()) {
            reader.setTypeFilter(type);
            log.info("🎯 Type filter (pdfToJson): {}", type);
        }
        
        return reader;
    }
}
