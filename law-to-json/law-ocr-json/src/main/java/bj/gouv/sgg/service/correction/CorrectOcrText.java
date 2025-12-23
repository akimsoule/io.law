package bj.gouv.sgg.service.correction;

import bj.gouv.sgg.entity.ErrorCorrection;

import java.util.List;

/**
 * Interface pour la correction de texte OCR
 */
public interface CorrectOcrText {

    void loadCorrectionCsvInDb();
    void loadDictionary();

    List<ErrorCorrection> loadCorrections();

    String applyCorrections(String text);
}
