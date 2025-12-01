package io.rdfforge.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for RDF Forge microservices.
 * Provides consistent API documentation across all services.
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:rdf-forge}")
    private String applicationName;

    @Value("${openapi.server.url:http://localhost:8000}")
    private String serverUrl;

    @Value("${keycloak.auth-server-url:http://localhost:8080}")
    private String keycloakUrl;

    @Bean
    public OpenAPI rdfForgeOpenAPI() {
        return new OpenAPI()
            .info(apiInfo())
            .externalDocs(externalDocs())
            .servers(List.of(
                new Server()
                    .url(serverUrl)
                    .description("RDF Forge API Gateway")
            ))
            .components(securityComponents())
            .addSecurityItem(new SecurityRequirement().addList("oauth2"));
    }

    private Info apiInfo() {
        return new Info()
            .title("RDF Forge - " + formatServiceName(applicationName))
            .description(getServiceDescription())
            .version("1.0.0")
            .contact(new Contact()
                .name("RDF Forge Team")
                .email("support@rdf-forge.io")
                .url("https://rdf-forge.io"))
            .license(new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0"));
    }

    private ExternalDocumentation externalDocs() {
        return new ExternalDocumentation()
            .description("RDF Forge Documentation")
            .url("https://docs.rdf-forge.io");
    }

    private Components securityComponents() {
        return new Components()
            .addSecuritySchemes("oauth2", new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("OAuth 2.0 authentication via Keycloak")
                .flows(new OAuthFlows()
                    .authorizationCode(new OAuthFlow()
                        .authorizationUrl(keycloakUrl + "/realms/rdfforge/protocol/openid-connect/auth")
                        .tokenUrl(keycloakUrl + "/realms/rdfforge/protocol/openid-connect/token")
                    )
                )
            )
            .addSecuritySchemes("bearer", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Bearer token authentication")
            );
    }

    private String formatServiceName(String name) {
        if (name == null) return "API";
        return name.replace("rdf-forge-", "")
                   .replace("-service", "")
                   .replace("-", " ")
                   .toUpperCase() + " Service API";
    }

    private String getServiceDescription() {
        return switch (applicationName) {
            case "rdf-forge-gateway" -> """
                RDF Forge API Gateway - The main entry point for all RDF Forge services.
                Routes requests to appropriate microservices and handles authentication.
                """;
            case "rdf-forge-pipeline-service" -> """
                Pipeline Service - Manages ETL pipeline definitions and execution workflows.
                Supports YAML, JSON, and Turtle pipeline definitions.
                """;
            case "rdf-forge-shacl-service" -> """
                SHACL Service - Manages SHACL shapes and performs RDF data validation.
                Supports shape creation, validation, and constraint checking.
                """;
            case "rdf-forge-job-service" -> """
                Job Service - Manages pipeline execution jobs, scheduling, and monitoring.
                Provides real-time job status updates and execution logs.
                """;
            case "rdf-forge-data-service" -> """
                Data Service - Handles data source management and file storage.
                Supports CSV, JSON, Excel, and Parquet formats.
                """;
            case "rdf-forge-dimension-service" -> """
                Dimension Service - Manages statistical dimensions and hierarchies.
                Supports cube dimension creation and code list management.
                """;
            case "rdf-forge-triplestore-service" -> """
                Triplestore Service - Manages triplestore connections and SPARQL operations.
                Supports multiple triplestore backends including Fuseki and GraphDB.
                """;
            default -> """
                RDF Forge API - Enterprise-grade RDF data processing and cube creation platform.
                Part of the Swiss Federal Archives data management ecosystem.
                """;
        };
    }
}