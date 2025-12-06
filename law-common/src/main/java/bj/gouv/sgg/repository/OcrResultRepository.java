package bj.gouv.sgg.repository;

import bj.gouv.sgg.model.OcrResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository pour gérer les résultats OCR
 */
@Repository
public interface OcrResultRepository extends JpaRepository<OcrResult, Long> {
    
    Optional<OcrResult> findByDocumentId(String documentId);
    
    boolean existsByDocumentId(String documentId);
}
