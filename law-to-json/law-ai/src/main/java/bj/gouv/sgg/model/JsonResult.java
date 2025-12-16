package bj.gouv.sgg.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class JsonResult {

    private final String json;
    private final double confidence; // 0.0 - 1.0
    private final String source; // IA|OCR and provider detail

}
