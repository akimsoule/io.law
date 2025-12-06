package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static bj.gouv.sgg.model.LawDocument.TYPE_DECRET;
import static bj.gouv.sgg.model.LawDocument.TYPE_LOI;

/**
 * Thread-safe reader qui génère tous les documents possibles (loi et decret) depuis START_YEAR jusqu'à maintenant
 */
@Slf4j
@Component
public class LawDocumentReader implements ItemReader<LawDocument> {
    
    private final LawProperties properties;
    private List<LawDocument> documents;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    
    public LawDocumentReader(LawProperties properties) {
        this.properties = properties;
    }
    
    @Override
    public synchronized LawDocument read() {
        if (documents == null) {
            documents = generateDocuments();
            log.info("Generated {} potential documents to fetch", documents.size());
        }
        
        int index = currentIndex.getAndIncrement();
        if (index < documents.size()) {
            return documents.get(index);
        }
        
        return null; // End of data
    }
    
    private List<LawDocument> generateDocuments() {
        List<LawDocument> docs = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        
        // Générer tous les documents possibles (lois et décrets)
        for (int year = properties.getEndYear(); year <= currentYear; year++) {
            for (int number = 1; number <= properties.getMaxNumberPerYear(); number++) {
                // Loi
                docs.add(createDocument(TYPE_LOI, year, number));
                // Decret
                docs.add(createDocument(TYPE_DECRET, year, number));
            }
        }
        
        return docs;
    }
    
    private LawDocument createDocument(String type, int year, int number) {
        // Appliquer le padding pour les numéros 1-9 (ex: 01, 02, ..., 09)
        String numberStr = String.format("%02d", number);
        String url = String.format("%s/%s-%d-%s", 
            properties.getBaseUrl(), type, year, numberStr);
        
        return LawDocument.builder()
            .type(type)
            .year(year)
            .number(number)
            .url(url)
            .status(LawDocument.ProcessingStatus.PENDING)
            .build();
    }
    
    public void reset() {
        currentIndex.set(0);
        documents = null;
    }
}
