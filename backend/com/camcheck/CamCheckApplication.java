package com.camcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;

/**
 * Main application class for CamCheck
 * A lightweight personal security camera system
 */
@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(
    info = @Info(
        title = "CamCheck API",
        version = "1.0",
        description = "API for CamCheck - Lightweight Personal Security Camera System",
        contact = @Contact(
            name = "CamCheck Support",
            email = "support@camcheck.example.com"
        ),
        license = @License(
            name = "MIT License",
            url = "https://opensource.org/licenses/MIT"
        )
    ),
    servers = {
        @Server(
            url = "/",
            description = "Default Server URL"
        )
    }
)
public class CamCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(CamCheckApplication.class, args);
    }
} 