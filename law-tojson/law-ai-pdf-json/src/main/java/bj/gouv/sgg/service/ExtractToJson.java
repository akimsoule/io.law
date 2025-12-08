package bj.gouv.sgg.service;

import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.model.LawDocument;

import java.nio.file.Path;

public interface ExtractToJson {

    JsonResult transform(LawDocument document, Path pdfPath) throws IAException;
    
}
