package io.rdfforge.cli.commands;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@ShellComponent
public class ShaclCommands {

    private final WebClient webClient;

    public ShaclCommands(WebClient webClient) {
        this.webClient = webClient;
    }

    @ShellMethod(key = "shape list", value = "List SHACL shapes")
    public String listShapes(
            @ShellOption(defaultValue = "") String projectId,
            @ShellOption(defaultValue = "") String category) {
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/shapes")
                        .queryParam("projectId", projectId)
                        .queryParam("category", category)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
    
    @ShellMethod(key = "shape validate", value = "Validate data against a shape")
    public String validateShape(
            @ShellOption UUID shapeId,
            @ShellOption String dataContent,
            @ShellOption(defaultValue = "TURTLE") String dataFormat) {
        
        String requestBody = String.format(
            "{\"shapeId\": \"%s\", \"dataContent\": \"%s\", \"dataFormat\": \"%s\"}", 
            shapeId, dataContent.replace("\"", "\\\""), dataFormat
        );

        return webClient.post()
                .uri("/shapes/validate")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
