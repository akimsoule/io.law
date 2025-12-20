package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.entity.LawArticleEntity;
import bj.gouv.sgg.repository.LawArticleRepository;
import bj.gouv.sgg.service.LawArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implémentation du service pour gérer les articles consolidés.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LawArticleServiceImpl implements LawArticleService {

    private final LawArticleRepository lawArticleRepository;

    @Override
    @Transactional
    public LawArticleEntity save(LawArticleEntity article) {
        return lawArticleRepository.save(article);
    }

    @Override
    @Transactional
    public List<LawArticleEntity> saveAll(List<LawArticleEntity> articles) {
        return lawArticleRepository.saveAll(articles);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LawArticleEntity> findByDocumentId(String documentId) {
        return lawArticleRepository.findByDocumentIdOrderByArticleNumber(documentId);
    }

    @Override
    @Transactional
    public void deleteByDocumentId(String documentId) {
        long count = lawArticleRepository.countByDocumentId(documentId);
        if (count > 0) {
            log.info("🗑️ Suppression de {} articles pour {}", count, documentId);
            lawArticleRepository.deleteByDocumentId(documentId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countByDocumentId(String documentId) {
        return lawArticleRepository.countByDocumentId(documentId);
    }
}
