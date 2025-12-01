package io.rdfforge.cli.commands;

import io.rdfforge.common.model.Dimension;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

@ShellComponent
public class DimensionCommands {

    private final WebClient webClient;

    public DimensionCommands(WebClient webClient) {
        this.webClient = webClient;
    }

    @ShellMethod(key = "dimension list", value = "List dimensions")
    public String listDimensions(
            @ShellOption(defaultValue = "") String projectId,
            @ShellOption(defaultValue = "") String search) {
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/dimensions")
                        .queryParam("projectId", projectId)
                        .queryParam("search", search)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @ShellMethod(key = "dimension get", value = "Get dimension by ID")
    public String getDimension(@ShellOption UUID id) {
        return webClient.get()
                .uri("/dimensions/{id}", id)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @ShellMethod(key = "dimension create", value = "Create a new dimension")
    public String createDimension(
            @ShellOption String name,
            @ShellOption String uri,
            @ShellOption(defaultValue = "KEY") String type) {
        
        Dimension dimension = Dimension.builder()
                .name(name)
                .uri(uri)
                .type(Dimension.DimensionType.valueOf(type))
                .build();

        return webClient.post()
                .uri("/dimensions")
                .bodyValue(dimension)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
