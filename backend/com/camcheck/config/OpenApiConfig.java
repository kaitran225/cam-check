package com.camcheck.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenAPI documentation
 */
@Configuration
public class OpenApiConfig {
    
    @Value("${camcheck.jwt.header:Authorization}")
    private String authHeader;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("CamCheck API")
                .description("API documentation for CamCheck camera monitoring system")
                .version("1.0")
                .contact(new Contact()
                    .name("CamCheck Support")
                    .email("support@camcheck.com")
                    .url("https://www.camcheck.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("http://www.apache.org/licenses/LICENSE-2.0.html")))
            .addServersItem(new Server().url("/").description("Default Server URL"))
            .addSecurityItem(new SecurityRequirement().addList("JWT"))
            .components(new Components()
                .addSecuritySchemes("JWT", 
                    new SecurityScheme()
                        .name(authHeader)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token authentication. Enter the token with the `Bearer ` prefix, e.g. \"Bearer eyJhbGciOiJIUzI1NiJ9...\"")));
    }
} 