package bj.gouv.sgg.service.correction;

import bj.gouv.sgg.entity.LawDocumentEntity;

import java.io.File;

/**
 * Interface pour la correction de texte OCR
 */
public interface CorrectOcrText {

    String parseOCRFile(LawDocumentEntity document, File file);

    String applyCorrections(String text);

}
