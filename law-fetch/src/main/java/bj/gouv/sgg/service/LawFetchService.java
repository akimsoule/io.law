package bj.gouv.sgg.service;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.repository.FetchResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

/**
 * Service pour le fetch d'un document unique
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LawFetchService {

    private final FetchResultRepository fetchResultRepository;
    private final LawProperties properties;
    private final RestTemplate restTemplate;

    public FetchResult fetchSingleDocument(String type, int year, int number) {
        String documentId = String.format("%s-%d-%d", type, year, number);
        String url = String.format("%s/%s", properties.getBaseUrl(), documentId);

        log.info("Fetching document: {} from {}", documentId, url);

        try {
            restTemplate.headForHeaders(url);
            
            // Vérifier si le FetchResult existe déjà
            FetchResult result = fetchResultRepository.findByDocumentId(documentId)
                    .orElse(FetchResult.builder()
                            .documentId(documentId)
                            .documentType(type)
                            .year(year)
                            .number(number)
                            .build());
            
            result.setUrl(url);
            result.setStatus("DOWNLOADED");
            result.setFetchedAt(LocalDateTime.now());
            result.setErrorMessage(null);

            fetchResultRepository.save(result);
            log.info("Successfully fetched: {}", documentId);
            return result;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Document not found (404): {}", documentId);
                
                FetchResult result = fetchResultRepository.findByDocumentId(documentId)
                        .orElse(FetchResult.builder()
                                .documentId(documentId)
                                .documentType(type)
                                .year(year)
                                .number(number)
                                .build());
                
                result.setUrl(url);
                result.setStatus("NOT_FOUND");
                result.setErrorMessage("404 Not Found");
                result.setFetchedAt(LocalDateTime.now());
                
                fetchResultRepository.save(result);
                return result;
            }
            throw e;
        }
    }
}
