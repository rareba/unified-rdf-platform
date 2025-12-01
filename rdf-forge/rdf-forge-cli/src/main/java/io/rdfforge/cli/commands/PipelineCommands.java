package io.rdfforge.cli.commands;

import io.rdfforge.common.model.Pipeline;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@ShellComponent
public class PipelineCommands {

    private final WebClient webClient;

    public PipelineCommands(WebClient webClient) {
        this.webClient = webClient;
    }

    @ShellMethod(key = "pipeline list", value = "List pipelines")
    public String listPipelines(
            @ShellOption(defaultValue = "") String projectId,
            @ShellOption(defaultValue = "") String search) {
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/pipelines")
                        .queryParam("projectId", projectId)
                        .queryParam("search", search)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @ShellMethod(key = "pipeline get", value = "Get pipeline by ID")
    public String getPipeline(@ShellOption UUID id) {
        return webClient.get()
                .uri("/pipelines/{id}", id)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @ShellMethod(key = "pipeline run", value = "Run a pipeline")
    public String runPipeline(@ShellOption UUID id) {
        // Assuming job creation triggers run
        return webClient.post()
                .uri("/jobs")
                .bodyValue("{\"pipelineId\": \"" + id + "\"}")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
    
    @ShellMethod(key = "pipeline validate", value = "Validate a pipeline")
    public String validatePipeline(@ShellOption UUID id) {
         return webClient.get()
                .uri("/pipelines/{id}", id)
                .retrieve()
                .bodyToMono(Pipeline.class)
                .flatMap(pipeline -> webClient.post()
                        .uri("/pipelines/validate")
                        .bodyValue(pipeline)
                        .retrieve()
                        .bodyToMono(String.class))
                .block();
    }
}
