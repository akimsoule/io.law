package bj.gouv.sgg.repository;

import bj.gouv.sgg.entity.ErrorCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ErrorCorrectionRepository extends JpaRepository<ErrorCorrection, Long> {

    List<ErrorCorrection> findByCorrectionTextIsNotNull();

    List<ErrorCorrection> findByCorrectionTextIsNull();

    Optional<ErrorCorrection> findByErrorFound(String word);

}