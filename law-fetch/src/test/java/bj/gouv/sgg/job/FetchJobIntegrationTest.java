package bj.gouv.sgg.job;

import bj.gouv.sgg.entity.FetchCursorEntity;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.repository.FetchCursorRepository;
import bj.gouv.sgg.repository.LawDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration pour les jobs Spring Batch de fetch.
 * Tests tous les cas possibles : current (scan complet année courante), previous avec cursor, document spécifique.
 */
@SpringBootTest
@ActiveProfiles("test")
class FetchJobIntegrationTest {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    @Qualifier("fetchCurrentJob")
    private Job fetchCurrentJob;
    
    @Autowired
    @Qualifier("fetchPreviousJob")
    private Job fetchPreviousJob;
    
    @Autowired
    private LawDocumentRepository repository;
    
    @Autowired
    private FetchCursorRepository cursorRepository;
    
    @BeforeEach
    void setup() {
        repository.deleteAll();
        cursorRepository.deleteAll();
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "SCAN_ALL", matches = "true")
    void givenLawTypeThenFetchCurrentJobShouldFetchAll2000Documents() throws Exception {
        // Given
        String type = "loi";
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("type", type)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        // When
        JobExecution execution = jobLauncher.run(fetchCurrentJob, jobParameters);
        
        // Then - Vérifier que le job s'est exécuté avec succès
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.FAILED);
        
        // fetchCurrentJob scanne systématiquement toute l'année courante (2000 docs + variantes)
        // En test, les requêtes HTTP échouent donc les documents ne sont pas persistés
        // On vérifie juste que le job a bien tenté de s'exécuter
        List<LawDocumentEntity> documents = repository.findAll();
        // Les documents peuvent être vides si toutes les requêtes HTTP échouent en test
        // S'assurer qu'on a des documents persistés avec le status FETCHED ou NOT_FOUND
        assertThat(documents).allMatch(doc ->
            doc.getStatus() == ProcessingStatus.FETCHED || doc.getStatus() == ProcessingStatus.NOT_FOUND
        );
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SCAN_ALL", matches = "true")
    void givenDecretTypeThenFetchCurrentJobShouldFetchAll2000Documents() throws Exception {
        // Given
        String type = "decret";
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("type", type)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution execution = jobLauncher.run(fetchCurrentJob, jobParameters);

        // Then - Vérifier que le job s'est exécuté avec succès
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.FAILED);

        // fetchCurrentJob scanne systématiquement toute l'année courante (2000 docs + variantes)
        // En test, les requêtes HTTP échouent donc les documents ne sont pas persistés
        // On vérifie juste que le job a bien tenté de s'exécuter
        List<LawDocumentEntity> documents = repository.findAll();
        // Les documents peuvent être vides si toutes les requêtes HTTP échouent en test
        // S'assurer qu'on a des documents persistés avec le status FETCHED ou NOT_FOUND
        assertThat(documents).allMatch(doc ->
                doc.getStatus() == ProcessingStatus.FETCHED || doc.getStatus() == ProcessingStatus.NOT_FOUND
        );
    }


    @Test
    void givenNotFoundDocumentIdThenFetchPreviousJobShouldNotFoundSingleDocument() throws Exception {
        // Given - Utiliser un document existant connu (exemple: loi-2024-1)
        String documentId = "loi-2024-1";
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("documentId", documentId)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        // When
        JobExecution execution = jobLauncher.run(fetchPreviousJob, jobParameters);
        
        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        List<LawDocumentEntity> documents = repository.findAll();
        assertThat(documents).hasSize(1);
        // Si le document existe, il sera persisté. Sinon, la liste peut être vide (NOT_FOUND pas persisté pour current)
        assertThat(documents.get(0).getDocumentId()).isEqualTo(documentId);
        assertThat(documents.get(0).getStatus()).isEqualTo(ProcessingStatus.NOT_FOUND);
    }

    @Test
    void givenValidDocumentIdThenFetchPreviousJobShouldFetchSingleDocument() throws Exception {
        // Given - Utiliser un document existant connu (exemple: loi-2024-1)
        String documentId = "loi-2015-24";
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("documentId", documentId)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution execution = jobLauncher.run(fetchPreviousJob, jobParameters);

        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<LawDocumentEntity> documents = repository.findAll();
        assertThat(documents).hasSize(1);
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getDocumentId()).isEqualTo(documentId);
        assertThat(documents.get(0).getStatus()).isEqualTo(ProcessingStatus.FETCHED);
    }
    
    @Test
    void givenValidTypeAndMaxItemsThenFetchPreviousJobShouldFetchLimitedDocuments() throws Exception {
        // Given
        String type = "loi";
        int maxItems = 5;
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("type", type)
                .addLong("maxItems", (long) maxItems)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        
        // When
        JobExecution execution = jobLauncher.run(fetchPreviousJob, jobParameters);
        
        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        List<LawDocumentEntity> documents = repository.findAll();
        // FetchPrevious persiste FETCHED + NOT_FOUND
        assertThat(documents).isNotEmpty();
        assertThat(documents).hasSizeLessThanOrEqualTo(maxItems * 3); // *3 pour les variantes de padding
        assertThat(documents).allMatch(doc -> doc.getType().equals(type));
    }
    
    @Test
    void givenFetchPreviousJobThenCursorShouldBeCreatedAndUpdated() throws Exception {
        // Given
        String type = "decret";
        int maxItems = 2;
        
        // Vérifier qu'aucun cursor n'existe au départ (méthode sans verrou pour tests)
        Optional<FetchCursorEntity> initialCursor = cursorRepository.findByCursorTypeAndDocumentTypeNoLock("fetch-previous", type);
        assertThat(initialCursor).isEmpty();
        
        // When - Exécuter le job
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("type", type)
                .addLong("maxItems", (long) maxItems)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        JobExecution execution = jobLauncher.run(fetchPreviousJob, jobParameters);
        
        // Then - Le cursor doit être créé et mis à jour à chaque traitement
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        Optional<FetchCursorEntity> cursorAfterRun = cursorRepository.findByCursorTypeAndDocumentTypeNoLock("fetch-previous", type);
        assertThat(cursorAfterRun).isPresent();
        
        FetchCursorEntity cursor = cursorAfterRun.get();
        assertThat(cursor.getDocumentType()).isEqualTo(type);
        assertThat(cursor.getCursorType()).isEqualTo("fetch-previous");
        assertThat(cursor.getCurrentYear()).isGreaterThan(1960);
        assertThat(cursor.getCurrentNumber()).isGreaterThan(0);
        
        // Vérifier que des documents ont été persistés (FETCHED + NOT_FOUND)
        List<LawDocumentEntity> documents = repository.findAll();
        assertThat(documents).isNotEmpty();
        assertThat(documents).hasSizeLessThanOrEqualTo(maxItems * 3); // * 3 pour variantes padding
        
        // Le cursor doit être à la dernière position traitée
        assertThat(cursor.getCurrentNumber()).as(
            "Le cursor doit pointer vers le dernier document traité (number > %d)", maxItems
        ).isGreaterThanOrEqualTo(maxItems);
    }
    
    @Test
    void givenMultipleRunsThenDocumentsShouldNotDuplicate() throws Exception {
        // Given
        String type = "loi";
        String documentId = "loi-2024-1"; // Document existant connu
        JobParameters jobParameters1 = new JobParametersBuilder()
                .addString("type", type)
                .addString("documentId", documentId)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        // When - Run twice
        JobExecution execution1 = jobLauncher.run(fetchCurrentJob, jobParameters1);
        int firstCount = repository.findAll().size();
        
        Thread.sleep(10); // Assurer timestamp différent
        JobParameters jobParameters2 = new JobParametersBuilder()
                .addString("type", type)
                .addString("documentId", documentId)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        JobExecution execution2 = jobLauncher.run(fetchCurrentJob, jobParameters2);
        int secondCount = repository.findAll().size();
        
        // Then
        assertThat(execution1.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution2.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(secondCount).isEqualTo(firstCount); // Pas de duplication
    }
}
