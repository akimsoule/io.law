package bj.gouv.sgg.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class Utils {

    public synchronized Set<String> getIds(String type, int currentYear, int currentNumber ) {
        Set<String> ids = new LinkedHashSet<>();
        String documentId = String.format("%s-%d-%d", type, currentYear, currentNumber);
        ids.add(documentId);

        // Ajouter les variantes avec padding pour num < 10
        if (currentNumber < 10) {
            ids.add(String.format("%s-%d-0%d", type, currentYear, currentNumber));
            ids.add(String.format("%s-%d-00%d", type, currentYear, currentNumber));
        }

        return ids;
    }
}
