package bj.gouv.sgg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Représente un article de loi ou décret.
 * <p>
 * Cette classe est copiée depuis law-ocr-json pour éviter une dépendance cyclique.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {

    /** Index de l'article (1, 2, 3...) */
    private int index;
    
    /** Contenu textuel de l'article */
    private String content;

}
