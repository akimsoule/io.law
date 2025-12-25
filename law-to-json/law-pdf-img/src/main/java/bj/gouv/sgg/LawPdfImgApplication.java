package bj.gouv.sgg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LawPdfImgApplication {
    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(LawPdfImgApplication.class, args)));
    }
}
