package br.edu.ucsal.clima.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI climaOpenAPI() {
        return new OpenAPI().info(new Info().title("Sistema de Controle e Monitoramento do Clima")
            .version("v1").description("API REST de controle e monitoramento meteorológico"))
            .schemaRequirement("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                .scheme("bearer").bearerFormat("JWT"))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
