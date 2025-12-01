package io.rdfforge.cli.commands;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@ShellComponent
public class TriplestoreCommands {

    private final WebClient webClient;

    public TriplestoreCommands(WebClient webClient) {
        this.webClient = webClient;
    }

    @ShellMethod(key = "store list", value = "List triplestore connections")
    public String listStores(@ShellOption(defaultValue = "") String projectId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/triplestores")
                        .queryParam("projectId", projectId)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @ShellMethod(key = "store connect", value = "Test connection to a triplestore")
    public String connectStore(@ShellOption UUID id) {
        return webClient.post()
                .uri("/triplestores/{id}/connect", id)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
    
    @ShellMethod(key = "store query", value = "Execute SPARQL query")
    public String queryStore(
            @ShellOption UUID id, 
            @ShellOption String query) {
        
        String requestBody = String.format("{\"query\": \"%s\"}", query.replace("\"", "\\\""));
        
        return webClient.post()
                .uri("/triplestores/{id}/sparql", id)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
    
    @ShellMethod(key = "store load", value = "Load RDF data into a graph")
    public String loadStore(
            @ShellOption UUID id,
            @ShellOption String graphUri,
            @ShellOption String content,
            @ShellOption(defaultValue = "TURTLE") String format) {
            
        String requestBody = String.format(
            "{\"graphUri\": \"%s\", \"content\": \"%s\", \"format\": \"%s\"}",
            graphUri, content.replace("\"", "\\\""), format
        );
        
        return webClient.post()
                .uri("/triplestores/{id}/upload", id)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
