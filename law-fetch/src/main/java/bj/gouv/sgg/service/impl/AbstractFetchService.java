package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.model.ProcessingStatus;
import bj.gouv.sgg.service.FetchService;
import bj.gouv.sgg.service.HttpCheckService;
import bj.gouv.sgg.service.LawDocumentService;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Classe abstraite de base pour les services de fetch.
 * Fournit la logique commune pour vérifier l'existence d'un document.
 */
@Slf4j
public abstract class AbstractFetchService implements FetchService {
    
    protected final AppConfig config;
    protected final LawDocumentService lawDocumentService;
    protected final HttpCheckService httpCheckService;
    
    public AbstractFetchService() {
        this.config = AppConfig.get();
        this.lawDocumentService = new LawDocumentService();
        this.httpCheckService = new HttpCheckService();
    }
    
    @Override
    public abstract void runDocument(String documentId);
    
    /**
     * Méthode abstraite à implémenter par les services spécialisés.
     * Définit la logique spécifique de fetch pour un type.
     */
    @Override
    public abstract void runType(String type);

}
