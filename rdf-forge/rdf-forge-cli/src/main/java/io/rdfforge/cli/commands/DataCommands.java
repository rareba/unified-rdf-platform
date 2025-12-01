package io.rdfforge.cli.commands;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.util.UUID;

@ShellComponent
public class DataCommands {

    private final WebClient webClient;

    public DataCommands(WebClient webClient) {
        this.webClient = webClient;
    }

    @ShellMethod(key = "data list", value = "List data sources")
    public String listData(
            @ShellOption(defaultValue = "") String projectId,
            @ShellOption(defaultValue = "") String format) {
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/data")
                        .queryParam("projectId", projectId)
                        .queryParam("format", format)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @ShellMethod(key = "data upload", value = "Upload a data file")
    public String uploadData(
            @ShellOption String filePath,
            @ShellOption(defaultValue = "UTF-8") String encoding,
            @ShellOption(defaultValue = "true") boolean analyze) {
        
        File file = new File(filePath);
        if (!file.exists()) {
            return "File not found: " + filePath;
        }

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new FileSystemResource(file));
        builder.part("encoding", encoding);
        builder.part("analyze", analyze);

        return webClient.post()
                .uri("/data/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
    
    @ShellMethod(key = "data preview", value = "Preview data source content")
    public String previewData(
            @ShellOption UUID id,
            @ShellOption(defaultValue = "10") int rows) {
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/data/{id}/preview")
                        .queryParam("rows", rows)
                        .build(id))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @ShellMethod(key = "data delete", value = "Delete a data source")
    public String deleteData(@ShellOption UUID id) {
        return webClient.delete()
                .uri("/data/{id}", id)
                .retrieve()
                .toBodilessEntity()
                .map(response -> "Data source deleted successfully")
                .block();
    }
}
