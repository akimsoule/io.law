package bj.gouv.sgg.util;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * Utilitaire pour parser et formater les dates.
 * Centralise la logique de conversion des dates utilisée dans ArticleExtractorService
 * et ArticleExtractorConfig.
 */
@Slf4j
public class DateParsingUtil {
    
    private static final Map<String, Integer> FRENCH_MONTHS = Map.ofEntries(
        Map.entry("janvier", 1),
        Map.entry("février", 2),
        Map.entry("fevrier", 2),  // Sans accent
        Map.entry("mars", 3),
        Map.entry("avril", 4),
        Map.entry("mai", 5),
        Map.entry("juin", 6),
        Map.entry("juillet", 7),
        Map.entry("août", 8),
        Map.entry("aout", 8),  // Sans accent
        Map.entry("septembre", 9),
        Map.entry("octobre", 10),
        Map.entry("novembre", 11),
        Map.entry("décembre", 12),
        Map.entry("decembre", 12)  // Sans accent
    );
    
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    /**
     * Formate une date depuis des composants texte (jour, mois français, année)
     * vers le format ISO (YYYY-MM-DD).
     * 
     * @param day Jour du mois (1-31)
     * @param month Nom du mois en français (janvier, février, etc.)
     * @param year Année (4 chiffres)
     * @return Optional contenant la date au format YYYY-MM-DD ou empty si parsing échoue
     */
    public static Optional<String> formatDate(String day, String month, String year) {
        if (day == null || month == null || year == null) {
            return Optional.empty();
        }
        
        try {
            Integer monthNum = FRENCH_MONTHS.get(month.toLowerCase());
            if (monthNum == null) {
                log.debug("Unknown month: {}", month);
                return Optional.empty();
            }
            
            int dayNum = Integer.parseInt(day);
            int yearNum = Integer.parseInt(year);
            
            LocalDate date = LocalDate.of(yearNum, monthNum, dayNum);
            return Optional.of(date.format(ISO_DATE_FORMATTER));
            
        } catch (NumberFormatException | DateTimeParseException e) {
            log.debug("Failed to format date: {} {} {}: {}", day, month, year, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Parse une date depuis une chaîne au format ISO (YYYY-MM-DD).
     * 
     * @param dateStr Chaîne représentant une date ISO
     * @return Optional contenant LocalDate ou empty si parsing échoue
     */
    public static Optional<LocalDate> parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return Optional.empty();
        }
        
        try {
            return Optional.of(LocalDate.parse(dateStr.trim(), ISO_DATE_FORMATTER));
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse date: {}: {}", dateStr, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Parse une date depuis un tableau de parties avec un index donné.
     * Utile pour parser des CSV où la date est à une position spécifique.
     * 
     * @param parts Tableau de chaînes
     * @param index Index de la date dans le tableau
     * @return Optional contenant LocalDate ou empty si parsing échoue ou index invalide
     */
    public static Optional<LocalDate> parseDate(String[] parts, int index) {
        if (parts == null || parts.length <= index) {
            return Optional.empty();
        }
        return parseDate(parts[index]);
    }
    
    // Prevent instantiation
    private DateParsingUtil() {}
}
