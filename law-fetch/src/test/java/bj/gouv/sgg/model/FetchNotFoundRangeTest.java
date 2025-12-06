package bj.gouv.sgg.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour FetchNotFoundRange
 */
class FetchNotFoundRangeTest {

    private FetchNotFoundRange range;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        range = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(10)
                .numberMax(20)
                .documentCount(11)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void testToRangeString_withMultipleNumbers() {
        // Given - une plage de 10 à 20
        // When
        String result = range.toRangeString();

        // Then
        assertEquals("loi;2025;10-20", result,
                "La plage devrait être formatée comme type;year;min-max");
    }

    @Test
    void testToRangeString_withSingleNumber() {
        // Given - une plage d'un seul numéro
        range.setNumberMin(15);
        range.setNumberMax(15);

        // When
        String result = range.toRangeString();

        // Then
        assertEquals("loi;2025;15", result,
                "Un seul numéro devrait être formaté sans tiret");
    }

    @Test
    void testToRangeString_withDecret() {
        // Given - un décret
        range.setDocumentType("decret");
        range.setYear(2024);
        range.setNumberMin(100);
        range.setNumberMax(200);

        // When
        String result = range.toRangeString();

        // Then
        assertEquals("decret;2024;100-200", result,
                "Le format devrait fonctionner avec les décrets");
    }

    @Test
    void testContains_numberInRange() {
        // Given - plage 10-20
        // When/Then
        assertTrue(range.contains(10), "Le numéro 10 (min) devrait être dans la plage");
        assertTrue(range.contains(15), "Le numéro 15 (milieu) devrait être dans la plage");
        assertTrue(range.contains(20), "Le numéro 20 (max) devrait être dans la plage");
    }

    @Test
    void testContains_numberOutsideRange() {
        // Given - plage 10-20
        // When/Then
        assertFalse(range.contains(9), "Le numéro 9 devrait être hors de la plage");
        assertFalse(range.contains(21), "Le numéro 21 devrait être hors de la plage");
        assertFalse(range.contains(1), "Le numéro 1 devrait être hors de la plage");
        assertFalse(range.contains(100), "Le numéro 100 devrait être hors de la plage");
    }

    @Test
    void testCanMergeWith_adjacentRanges() {
        // Given - plage1: 10-20, plage2: 21-30 (adjacentes)
        FetchNotFoundRange other = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(21)
                .numberMax(30)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // When/Then
        assertTrue(range.canMergeWith(other),
                "Les plages adjacentes devraient pouvoir être fusionnées");
        assertTrue(other.canMergeWith(range),
                "La fusion devrait être symétrique");
    }

    @Test
    void testCanMergeWith_overlappingRanges() {
        // Given - plage1: 10-20, plage2: 15-25 (chevauchement)
        FetchNotFoundRange other = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(15)
                .numberMax(25)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // When/Then
        assertTrue(range.canMergeWith(other),
                "Les plages chevauchantes devraient pouvoir être fusionnées");
    }

    @Test
    void testCanMergeWith_gapBetweenRanges() {
        // Given - plage1: 10-20, plage2: 22-30 (gap de 1)
        FetchNotFoundRange other = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(22)
                .numberMax(30)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // When/Then
        assertFalse(range.canMergeWith(other),
                "Les plages avec un gap > 1 ne devraient pas pouvoir être fusionnées");
    }

    @Test
    void testCanMergeWith_differentTypes() {
        // Given - plage1: loi, plage2: decret
        FetchNotFoundRange other = FetchNotFoundRange.builder()
                .documentType("decret")
                .year(2025)
                .numberMin(10)
                .numberMax(20)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // When/Then
        assertFalse(range.canMergeWith(other),
                "Les plages de types différents ne devraient pas pouvoir être fusionnées");
    }

    @Test
    void testCanMergeWith_differentYears() {
        // Given - plage1: 2025, plage2: 2024
        FetchNotFoundRange other = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2024)
                .numberMin(10)
                .numberMax(20)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // When/Then
        assertFalse(range.canMergeWith(other),
                "Les plages d'années différentes ne devraient pas pouvoir être fusionnées");
    }

    @Test
    void testCanMergeWith_completelyDisjoint() {
        // Given - plage1: 10-20, plage2: 50-60 (très éloignées)
        FetchNotFoundRange other = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(50)
                .numberMax(60)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // When/Then
        assertFalse(range.canMergeWith(other),
                "Les plages disjointes ne devraient pas pouvoir être fusionnées");
    }

    @Test
    void testMergeWith_adjacentRanges() {
        // Given - plage1: 10-20, plage2: 21-30
        FetchNotFoundRange other = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(21)
                .numberMax(30)
                .createdAt(now)
                .updatedAt(now)
                .build();

        LocalDateTime beforeMerge = range.getUpdatedAt();

        // When
        range.mergeWith(other);

        // Then
        assertEquals(10, range.getNumberMin(), "Le min devrait être 10");
        assertEquals(30, range.getNumberMax(), "Le max devrait être 30");
        assertTrue(range.getUpdatedAt().isAfter(beforeMerge) || range.getUpdatedAt().isEqual(beforeMerge),
                "updatedAt devrait être mis à jour");
    }

    @Test
    void testMergeWith_overlappingRanges() {
        // Given - plage1: 10-20, plage2: 15-25
        FetchNotFoundRange other = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(15)
                .numberMax(25)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // When
        range.mergeWith(other);

        // Then
        assertEquals(10, range.getNumberMin(), "Le min devrait être 10");
        assertEquals(25, range.getNumberMax(), "Le max devrait être 25");
    }

    @Test
    void testMergeWith_otherRangeIsSmaller() {
        // Given - plage1: 10-30, plage2: 15-20 (contenue dans plage1)
        FetchNotFoundRange other = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(15)
                .numberMax(20)
                .createdAt(now)
                .updatedAt(now)
                .build();

        range.setNumberMax(30);

        // When
        range.mergeWith(other);

        // Then
        assertEquals(10, range.getNumberMin(), "Le min devrait rester 10");
        assertEquals(30, range.getNumberMax(), "Le max devrait rester 30");
    }

    @Test
    void testMergeWith_otherRangeIsLarger() {
        // Given - plage1: 15-20, plage2: 10-30 (contient plage1)
        range.setNumberMin(15);
        range.setNumberMax(20);

        FetchNotFoundRange other = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(10)
                .numberMax(30)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // When
        range.mergeWith(other);

        // Then
        assertEquals(10, range.getNumberMin(), "Le min devrait être 10");
        assertEquals(30, range.getNumberMax(), "Le max devrait être 30");
    }

    @Test
    void testMergeWith_reverseOrder() {
        // Given - plage1: 20-30, plage2: 10-19 (l'autre est avant)
        range.setNumberMin(20);
        range.setNumberMax(30);

        FetchNotFoundRange other = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(10)
                .numberMax(19)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // When
        range.mergeWith(other);

        // Then
        assertEquals(10, range.getNumberMin(), "Le min devrait être 10");
        assertEquals(30, range.getNumberMax(), "Le max devrait être 30");
    }

    @Test
    void testBuilder() {
        // Given/When - construction via builder
        FetchNotFoundRange newRange = FetchNotFoundRange.builder()
                .id(1L)
                .documentType("decret")
                .year(2024)
                .numberMin(5)
                .numberMax(15)
                .documentCount(11)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Then
        assertEquals(1L, newRange.getId());
        assertEquals("decret", newRange.getDocumentType());
        assertEquals(2024, newRange.getYear());
        assertEquals(5, newRange.getNumberMin());
        assertEquals(15, newRange.getNumberMax());
        assertEquals(11, newRange.getDocumentCount());
        assertEquals(now, newRange.getCreatedAt());
        assertEquals(now, newRange.getUpdatedAt());
    }

    @Test
    void testSettersAndGetters() {
        // Given - nouvelle instance
        FetchNotFoundRange newRange = new FetchNotFoundRange();
        LocalDateTime testTime = LocalDateTime.of(2025, 6, 1, 10, 0);

        // When - utilisation des setters
        newRange.setId(99L);
        newRange.setDocumentType("loi");
        newRange.setYear(2023);
        newRange.setNumberMin(1);
        newRange.setNumberMax(100);
        newRange.setDocumentCount(100);
        newRange.setCreatedAt(testTime);
        newRange.setUpdatedAt(testTime);

        // Then - vérification des getters
        assertEquals(99L, newRange.getId());
        assertEquals("loi", newRange.getDocumentType());
        assertEquals(2023, newRange.getYear());
        assertEquals(1, newRange.getNumberMin());
        assertEquals(100, newRange.getNumberMax());
        assertEquals(100, newRange.getDocumentCount());
        assertEquals(testTime, newRange.getCreatedAt());
        assertEquals(testTime, newRange.getUpdatedAt());
    }

    @Test
    void testEquals_sameValues() {
        // Given - deux plages identiques (sauf id)
        FetchNotFoundRange range1 = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(10)
                .numberMax(20)
                .documentCount(11)
                .createdAt(now)
                .updatedAt(now)
                .build();

        FetchNotFoundRange range2 = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(10)
                .numberMax(20)
                .documentCount(11)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // When/Then - grâce à @Data de Lombok
        assertEquals(range1, range2, "Deux plages avec les mêmes valeurs devraient être égales");
        assertEquals(range1.hashCode(), range2.hashCode(), "Les hashCodes devraient être identiques");
    }

    @Test
    void testEquals_differentValues() {
        // Given - deux plages différentes
        FetchNotFoundRange range1 = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(10)
                .numberMax(20)
                .build();

        FetchNotFoundRange range2 = FetchNotFoundRange.builder()
                .documentType("loi")
                .year(2025)
                .numberMin(30)
                .numberMax(40)
                .build();

        // When/Then
        assertNotEquals(range1, range2, "Deux plages avec des valeurs différentes ne devraient pas être égales");
    }

    @Test
    void testToString() {
        // Given
        range.setId(123L);

        // When
        String result = range.toString();

        // Then
        assertNotNull(result);
        assertTrue(result.contains("loi"), "toString devrait contenir le type");
        assertTrue(result.contains("2025"), "toString devrait contenir l'année");
        assertTrue(result.contains("10"), "toString devrait contenir le min");
        assertTrue(result.contains("20"), "toString devrait contenir le max");
    }
}
