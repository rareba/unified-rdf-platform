package io.rdfforge.cli.commands;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@ShellComponent
public class JobCommands {

    private final WebClient webClient;

    public JobCommands(WebClient webClient) {
        this.webClient = webClient;
    }

    @ShellMethod(key = "job list", value = "List jobs")
    public String listJobs(
            @ShellOption(defaultValue = "") String status,
            @ShellOption(defaultValue = "") String pipelineId) {
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/jobs")
                        .queryParam("status", status)
                        .queryParam("pipelineId", pipelineId)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @ShellMethod(key = "job status", value = "Get job status")
    public String getJobStatus(@ShellOption UUID id) {
        return webClient.get()
                .uri("/jobs/{id}", id)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
    
    @ShellMethod(key = "job logs", value = "Get job logs")
    public String getJobLogs(@ShellOption UUID id) {
        return webClient.get()
                .uri("/jobs/{id}/logs", id)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @ShellMethod(key = "job cancel", value = "Cancel a running job")
    public String cancelJob(@ShellOption UUID id) {
        return webClient.delete()
                .uri("/jobs/{id}", id)
                .retrieve()
                .toBodilessEntity()
                .map(response -> "Job cancelled successfully")
                .block();
    }
    
    @ShellMethod(key = "job retry", value = "Retry a failed job")
    public String retryJob(@ShellOption UUID id) {
        return webClient.post()
                .uri("/jobs/{id}/retry", id)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
