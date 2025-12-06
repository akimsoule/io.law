package bj.gouv.sgg.repository;

import bj.gouv.sgg.model.ProcessingStatus;
import bj.gouv.sgg.model.ProcessingStatus.ProcessingState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessingStatusRepository extends JpaRepository<ProcessingStatus, Long> {
    
    Optional<ProcessingStatus> findByPdfFileName(String pdfFileName);
    
    List<ProcessingStatus> findByState(ProcessingState state);
    
    List<ProcessingStatus> findByStateAndConfidenceScoreLessThan(ProcessingState state, Double threshold);
    
    long countByState(ProcessingState state);
}
