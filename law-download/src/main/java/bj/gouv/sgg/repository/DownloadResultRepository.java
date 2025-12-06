package bj.gouv.sgg.repository;

import bj.gouv.sgg.model.DownloadResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository pour gérer les résultats de téléchargement
 */
@Repository
public interface DownloadResultRepository extends JpaRepository<DownloadResult, Long> {
    
    Optional<DownloadResult> findByDocumentId(String documentId);
    
    boolean existsByDocumentId(String documentId);
    
    Optional<DownloadResult> findBySha256(String sha256);
}
