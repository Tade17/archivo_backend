package com.municipalidadsanjose.archivo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearerAuth";

    // Declara el esquema de seguridad para que Swagger UI muestre el botón
    // "Authorize": ahí se pega el JWT (sin el prefijo "Bearer ") una sola vez,
    // y Swagger lo agrega automáticamente a cada "Try it out" siguiente.
    @Bean
    public OpenAPI archivoOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Archivo Backend API")
                        .description("Sistema de digitalización y gestión de archivo físico — Municipalidad Distrital de San José")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT,
                        new SecurityScheme()
                                .name(ESQUEMA_JWT)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
