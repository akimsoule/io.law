package bj.gouv.sgg.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Entité pour suivi des erreurs OCR et corrections proposées.
 *
 * Améliorations :
 * - Valeurs par défaut pour éviter les nulls
 * - Normalisation en @PrePersist
 * - equals/hashCode basés sur l'id (sécurisé)
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorCorrection implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String errorFound;

    @Builder.Default
    @Column(nullable = false)
    private int errorCount = 0;

    @Column
    private String correctionText;

    @Builder.Default
    @Column(nullable = false)
    private boolean correctionIsAutomatic = false;

    @PrePersist
    private void prePersist() {
        if (errorFound != null) {
            errorFound = errorFound.trim();
            if (errorFound.isEmpty()) errorFound = null;
        }
        if (errorCount < 0) {
            errorCount = 0;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErrorCorrection that = (ErrorCorrection) o;
        if (this.id == null || that.id == null) return false;
        return this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }
}
