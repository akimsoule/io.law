package bj.gouv.sgg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadata {
    private String lawTitle;
    private String promulgationDate;
    private String promulgationCity;
    
    @Builder.Default
    private List<Signatory> signatories = new ArrayList<>();
}
