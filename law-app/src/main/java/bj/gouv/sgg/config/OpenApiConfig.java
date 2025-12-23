package bj.gouv.sgg.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("IO.Law Orchestrator API")
                        .version("2.0.0")
                        .description("API to trigger and monitor batch jobs for law processing")
                );
    }
}
