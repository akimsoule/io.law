package bj.gouv.sgg.service;

import bj.gouv.sgg.model.FetchNotFound;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchNotFoundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service pour gérer les documents NOT_FOUND (404)
 * Système simple : une ligne par document introuvable
 * Idempotence garantie par contrainte UNIQUE + catch DataIntegrityViolationException
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotFoundService {

    private final FetchNotFoundRepository repository;

    /**
     * Enregistre un document comme NOT_FOUND
     * Idempotent : ignore silencieusement les duplicates
     * @param document Document à enregistrer comme NOT_FOUND
     */
    public void addNotFoundDocument(LawDocument document) {
        String documentType = document.getType();
        int year = document.getYear();
        int number = document.getNumber();

        try {
            FetchNotFound notFound = FetchNotFound.builder()
                .documentType(documentType)
                .year(year)
                .number(number)
                .build();
            
            repository.save(notFound);
            
            log.debug("✅ Marked as NOT_FOUND: {}", document.getDocumentId());
            
        } catch (DataIntegrityViolationException e) {
            // Document déjà marqué NOT_FOUND, c'est OK (idempotence)
            log.trace("⏭️ Already marked as NOT_FOUND: {}", document.getDocumentId());
        } catch (Exception e) {
            // Log erreur mais ne pas casser le job
            log.warn("⚠️ Unexpected error marking {} as NOT_FOUND: {}", 
                document.getDocumentId(), e.getMessage());
        }
    }

    /**
     * Enregistre plusieurs documents NOT_FOUND en batch
     * Optimisé : sauvegarde tous les documents en une seule transaction
     * Idempotent : ignore silencieusement les duplicates
     * @param documents Liste des documents NOT_FOUND
     */
    public void addNotFoundDocuments(List<LawDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        // Filtrer uniquement les documents NOT_FOUND
        List<FetchNotFound> notFoundList = documents.stream()
            .filter(doc -> !doc.isExists()) // FAILED status = not exists
            .map(doc -> FetchNotFound.builder()
                .documentType(doc.getType())
                .year(doc.getYear())
                .number(doc.getNumber())
                .build())
            .toList();

        if (notFoundList.isEmpty()) {
            return;
        }

        try {
            // ✅ Batch INSERT : sauvegarder tout en une fois
            repository.saveAll(notFoundList);
            log.debug("✅ Marked {} documents as NOT_FOUND (batch)", notFoundList.size());
            
        } catch (DataIntegrityViolationException e) {
            // Des duplicates existent, fallback sur sauvegarde 1 à 1
            log.debug("⚠️ Batch insert failed (duplicates), retrying one-by-one");
            notFoundList.forEach(notFound -> {
                try {
                    repository.save(notFound);
                } catch (DataIntegrityViolationException ignored) {
                    // Déjà présent, c'est OK (idempotence)
                }
            });
            
        } catch (Exception e) {
            // Log erreur mais ne pas casser le job
            log.warn("⚠️ Unexpected error during batch NOT_FOUND insert: {}", e.getMessage());
        }
    }

    /**
     * Vérifie si un document est dans les NOT_FOUND
     * @return true si le document est marqué comme NOT_FOUND
     */
    public boolean isInNotFoundRange(String documentType, int year, int number) {
        return repository.existsByDocumentTypeAndYearAndNumber(documentType, year, number);
    }
    
    /**
     * Compte le nombre de documents NOT_FOUND pour un type et une année
     * @return Nombre de documents NOT_FOUND
     */
    public long countNotFoundByTypeAndYear(String documentType, int year) {
        return repository.countByTypeAndYear(documentType, year);
    }
}
