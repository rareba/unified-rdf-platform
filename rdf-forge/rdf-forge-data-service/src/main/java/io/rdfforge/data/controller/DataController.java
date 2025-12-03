package io.rdfforge.data.controller;

import io.rdfforge.data.entity.DataSourceEntity;
import io.rdfforge.data.entity.DataSourceEntity.DataFormat;
import io.rdfforge.data.service.DataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data")
@Tag(name = "Data", description = "Data source management API")
@CrossOrigin(origins = "*")
public class DataController {
    
    private final DataService dataService;
    
    public DataController(DataService dataService) {
        this.dataService = dataService;
    }
    
    @GetMapping
    @Operation(summary = "List data sources", description = "Get paginated list of data sources")
    public ResponseEntity<Page<DataSourceEntity>> getDataSources(
        @RequestParam(required = false) UUID projectId,
        @RequestParam(required = false) DataFormat format,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(dataService.getDataSources(projectId, format, search, page, size));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get data source", description = "Get data source details by ID")
    public ResponseEntity<DataSourceEntity> getDataSource(@PathVariable UUID id) {
        return dataService.getDataSource(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/upload")
    @Operation(summary = "Upload data source", description = "Upload a new data file")
    public ResponseEntity<DataSourceEntity> uploadDataSource(
        @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "UTF-8") String encoding,
        @RequestParam(defaultValue = "true") boolean analyze
    ) throws IOException {
        DataSourceEntity entity = dataService.uploadDataSource(file, encoding, analyze, null);
        return ResponseEntity.ok(entity);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete data source", description = "Delete a data source")
    public ResponseEntity<Void> deleteDataSource(@PathVariable UUID id) throws IOException {
        dataService.deleteDataSource(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}/preview")
    @Operation(summary = "Preview data", description = "Get preview rows from data source")
    public ResponseEntity<Map<String, Object>> previewDataSource(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "100") int rows,
        @RequestParam(defaultValue = "0") int offset
    ) throws IOException {
        return ResponseEntity.ok(dataService.previewDataSource(id, rows, offset));
    }
    
    @GetMapping("/{id}/download")
    @Operation(summary = "Download data", description = "Download data source file")
    public ResponseEntity<InputStreamResource> downloadDataSource(@PathVariable UUID id) throws IOException {
        DataSourceEntity entity = dataService.getDataSource(id)
            .orElseThrow(() -> new RuntimeException("Data source not found: " + id));
        
        InputStream inputStream = dataService.downloadDataSource(id);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entity.getOriginalFilename() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(entity.getSizeBytes())
            .body(new InputStreamResource(inputStream));
    }
    
    @PostMapping("/{id}/analyze")
    @Operation(summary = "Analyze data", description = "Analyze data source structure and stats")
    public ResponseEntity<Map<String, Object>> analyzeDataSource(@PathVariable UUID id) throws IOException {
        return ResponseEntity.ok(dataService.analyzeDataSource(id));
    }
    
    @PostMapping("/detect-format")
    @Operation(summary = "Detect format", description = "Detect file format from upload")
    public ResponseEntity<Map<String, Object>> detectFormat(@RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        String format = "csv";
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".json")) format = "json";
            else if (lower.endsWith(".xlsx")) format = "xlsx";
            else if (lower.endsWith(".parquet")) format = "parquet";
            else if (lower.endsWith(".xml")) format = "xml";
            else if (lower.endsWith(".tsv")) format = "tsv";
        }
        
        return ResponseEntity.ok(Map.of(
            "format", format,
            "encoding", "UTF-8",
            "delimiter", format.equals("tsv") ? "\t" : ","
        ));
    }
}
