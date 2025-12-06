package bj.gouv.sgg.service;

import bj.gouv.sgg.model.FetchNotFoundRange;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchNotFoundRangeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service pour gérer les plages de documents NOT_FOUND
 * Consolide automatiquement les documents adjacents en plages
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotFoundRangeService {
    
    private final FetchNotFoundRangeRepository repository;
    
    /**
     * Ajoute un document NOT_FOUND et consolide les plages automatiquement
     * @param document Le document NOT_FOUND à ajouter
     */
    @Transactional
    public void addNotFoundDocument(LawDocument document) {
        String documentType = document.getType();
        Integer year = document.getYear();
        Integer number = document.getNumber();
        
        // Récupérer les plages qui peuvent être fusionnées avec ce nouveau document
        List<FetchNotFoundRange> overlappingRanges = repository.findOverlappingRanges(documentType, year, number);
        
        if (overlappingRanges.isEmpty()) {
            // Créer une nouvelle plage avec un seul document
            LocalDateTime now = LocalDateTime.now();
            FetchNotFoundRange newRange = FetchNotFoundRange.builder()
                .documentType(documentType)
                .year(year)
                .numberMin(number)
                .numberMax(number)
                .documentCount(1)
                .createdAt(now)
                .updatedAt(now)
                .build();
            repository.save(newRange);
            log.debug("Created new NOT_FOUND range: {}", newRange.toRangeString());
        } else {
            // Fusionner toutes les plages chevauchantes + le nouveau document
            FetchNotFoundRange mergedRange = overlappingRanges.get(0);
            mergedRange.setNumberMin(Math.min(mergedRange.getNumberMin(), number));
            mergedRange.setNumberMax(Math.max(mergedRange.getNumberMax(), number));
            mergedRange.setDocumentCount(mergedRange.getNumberMax() - mergedRange.getNumberMin() + 1);
            mergedRange.setUpdatedAt(LocalDateTime.now());
            
            // Fusionner avec les autres plages si nécessaire
            for (int i = 1; i < overlappingRanges.size(); i++) {
                FetchNotFoundRange other = overlappingRanges.get(i);
                mergedRange.mergeWith(other);
                repository.delete(other);
            }
            
            repository.save(mergedRange);
            log.debug("Merged into NOT_FOUND range: {} (merged {} ranges)", 
                mergedRange.toRangeString(), overlappingRanges.size());
        }
    }
    
    /**
     * Ajoute plusieurs documents NOT_FOUND en batch
     * @param documents Liste des documents NOT_FOUND
     */
    @Transactional
    public void addNotFoundDocuments(List<LawDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        
        // Grouper par type et année pour optimiser
        documents.stream()
            .filter(doc -> !doc.isExists())
            .forEach(this::addNotFoundDocument);
        
        log.info("Processed {} NOT_FOUND documents into ranges", documents.size());
    }
    
    /**
     * Vérifie si un document est dans une plage NOT_FOUND
     */
    public boolean isInNotFoundRange(String type, Integer year, Integer number) {
        return repository.isInNotFoundRange(type, year, number);
    }
    
    /**
     * Récupère toutes les plages pour un type et une année
     */
    public List<FetchNotFoundRange> getRanges(String type, Integer year) {
        return repository.findByDocumentTypeAndYearOrderByNumberMinAsc(type, year);
    }
    
    /**
     * Récupère toutes les plages triées
     */
    public List<FetchNotFoundRange> getAllRanges() {
        return repository.findAllByOrderByDocumentTypeAscYearDescNumberMinAsc();
    }
    
    /**
     * Consolide les plages existantes pour une année donnée
     * Utile pour nettoyer les plages fragmentées
     */
    @Transactional
    public int consolidateRanges(String type, Integer year) {
        List<FetchNotFoundRange> ranges = repository.findByDocumentTypeAndYearOrderByNumberMinAsc(type, year);
        
        if (ranges.size() <= 1) {
            return 0;
        }
        
        List<FetchNotFoundRange> consolidated = new ArrayList<>();
        FetchNotFoundRange current = ranges.get(0);
        int mergedCount = 0;
        
        for (int i = 1; i < ranges.size(); i++) {
            FetchNotFoundRange next = ranges.get(i);
            
            if (current.canMergeWith(next)) {
                current.mergeWith(next);
                repository.delete(next);
                mergedCount++;
            } else {
                consolidated.add(current);
                current = next;
            }
        }
        consolidated.add(current);
        
        repository.saveAll(consolidated);
        log.info("Consolidated {} ranges for {}-{} into {} ranges", 
            ranges.size(), type, year, consolidated.size());
        
        return mergedCount;
    }
}
