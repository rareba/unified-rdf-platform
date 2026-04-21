package io.rdfforge.data.controller;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.data.entity.DataSourceEntity;
import io.rdfforge.data.entity.DataSourceEntity.DataFormat;
import io.rdfforge.data.format.DataFormatInfo;
import io.rdfforge.data.format.DataFormatRegistry;
import io.rdfforge.data.service.DataService;
import io.rdfforge.data.service.FileStorageService;
import io.rdfforge.data.storage.StorageProviderInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data")
@Tag(name = "Data", description = "Data source management API")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class DataController {

    private final DataService dataService;
    private final DataFormatRegistry formatRegistry;
    private final FileStorageService fileStorageService;

    public DataController(DataService dataService, DataFormatRegistry formatRegistry, FileStorageService fileStorageService) {
        this.dataService = dataService;
        this.formatRegistry = formatRegistry;
        this.fileStorageService = fileStorageService;
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
    public ResponseEntity<DataSourceEntity> getDataSource(@PathVariable UUID id, @CurrentUser AuthUser user) {
        DataSourceEntity entity = dataService.getDataSource(id)
            .orElseThrow(() -> new ResourceNotFoundException("DataSource", id.toString()));
        requireOwnerOrAdmin(entity, user, "read");
        return ResponseEntity.ok(entity);
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload data source", description = "Upload a new data file")
    public ResponseEntity<DataSourceEntity> uploadDataSource(
        @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "UTF-8") String encoding,
        @RequestParam(defaultValue = "true") boolean analyze,
        @CurrentUser AuthUser user
    ) throws IOException {
        // uploadedBy is now populated from the gateway-trusted identity rather than
        // left null. This closes the "who owns this file?" gap that blocked
        // ownership enforcement on reads/deletes.
        DataSourceEntity entity = dataService.uploadDataSource(file, encoding, analyze, user.id());
        return ResponseEntity.ok(entity);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete data source", description = "Delete a data source")
    public ResponseEntity<Void> deleteDataSource(@PathVariable UUID id, @CurrentUser AuthUser user) throws IOException {
        DataSourceEntity entity = dataService.getDataSource(id)
            .orElseThrow(() -> new ResourceNotFoundException("DataSource", id.toString()));
        requireOwnerOrAdmin(entity, user, "delete");
        dataService.deleteDataSource(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/preview")
    @Operation(summary = "Preview data", description = "Get preview rows from data source")
    public ResponseEntity<Map<String, Object>> previewDataSource(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "100") int rows,
        @RequestParam(defaultValue = "0") int offset,
        @CurrentUser AuthUser user
    ) throws IOException {
        DataSourceEntity entity = dataService.getDataSource(id)
            .orElseThrow(() -> new ResourceNotFoundException("DataSource", id.toString()));
        requireOwnerOrAdmin(entity, user, "preview");
        return ResponseEntity.ok(dataService.previewDataSource(id, rows, offset));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download data", description = "Download data source file")
    public ResponseEntity<InputStreamResource> downloadDataSource(@PathVariable UUID id, @CurrentUser AuthUser user) throws IOException {
        DataSourceEntity entity = dataService.getDataSource(id)
            .orElseThrow(() -> new ResourceNotFoundException("DataSource", id.toString()));
        requireOwnerOrAdmin(entity, user, "download");

        InputStream inputStream = dataService.downloadDataSource(id);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entity.getOriginalFilename() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(entity.getSizeBytes())
            .body(new InputStreamResource(inputStream));
    }

    @PostMapping("/{id}/analyze")
    @Operation(summary = "Analyze data", description = "Analyze data source structure and stats")
    public ResponseEntity<Map<String, Object>> analyzeDataSource(@PathVariable UUID id, @CurrentUser AuthUser user) throws IOException {
        DataSourceEntity entity = dataService.getDataSource(id)
            .orElseThrow(() -> new ResourceNotFoundException("DataSource", id.toString()));
        requireOwnerOrAdmin(entity, user, "analyze");
        return ResponseEntity.ok(dataService.analyzeDataSource(id));
    }
    
    @PostMapping("/detect-format")
    @Operation(summary = "Detect format", description = "Detect file format from upload")
    public ResponseEntity<Map<String, Object>> detectFormat(@RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename();

        // Use registry to detect format
        String format = formatRegistry.detectFormat(filename).orElse("csv");

        // Get format info for additional options
        DataFormatInfo formatInfo = formatRegistry.getFormatInfo(format).orElse(null);

        String delimiter = ",";
        if (filename != null && filename.toLowerCase().endsWith(".tsv")) {
            delimiter = "\t";
        }

        return ResponseEntity.ok(Map.of(
            "format", format,
            "encoding", "UTF-8",
            "delimiter", delimiter,
            "formatInfo", formatInfo != null ? formatInfo : Map.of()
        ));
    }

    // ==================== Format Discovery API ====================

    @GetMapping("/formats")
    @Operation(summary = "List data format handlers",
        description = "Return every known format (available and stub/coming-soon). "
            + "Clients should use the returned 'available' flag to decide whether "
            + "to offer the format for upload.")
    public ResponseEntity<List<Map<String, Object>>> getAvailableFormats() {
        return ResponseEntity.ok(formatRegistry.getAvailableFormats().stream()
            .map(this::toFormatSummary)
            .toList());
    }

    @GetMapping("/formats/supported")
    @Operation(summary = "List supported (available) formats",
        description = "Return only formats that are fully implemented and accept uploads.")
    public ResponseEntity<List<Map<String, Object>>> getSupportedFormats() {
        return ResponseEntity.ok(formatRegistry.getSupportedFormats().stream()
            .map(this::toFormatSummary)
            .toList());
    }

    /**
     * Project a {@link DataFormatInfo} into the compact view consumed by the UI.
     * Exposes the handler capability flags as explicit operation booleans so the
     * dialog never has to interpret the capability string list.
     */
    private Map<String, Object> toFormatSummary(DataFormatInfo info) {
        List<String> caps = info.capabilities() != null ? info.capabilities() : List.of();
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", info.format());
        // Keep "format" for backwards-compatibility with existing clients and tests
        // that looked up the identifier under that key before /formats was enriched.
        m.put("format", info.format());
        m.put("displayName", info.displayName());
        m.put("description", info.description());
        m.put("mimeType", info.mimeType());
        m.put("extensions", info.fileExtensions());
        m.put("available", info.available());
        m.put("unavailableReason", info.unavailableReason());
        m.put("operations", Map.of(
            "preview", info.supportsPreview() && caps.contains(DataFormatInfo.CAPABILITY_PREVIEW),
            "parse", caps.contains(DataFormatInfo.CAPABILITY_READ),
            "ingest", info.available() && caps.contains(DataFormatInfo.CAPABILITY_READ),
            "analyze", info.supportsAnalysis() && caps.contains(DataFormatInfo.CAPABILITY_ANALYZE),
            "streaming", info.supportsStreaming() && caps.contains(DataFormatInfo.CAPABILITY_STREAMING),
            "write", caps.contains(DataFormatInfo.CAPABILITY_WRITE)
        ));
        m.put("options", info.options());
        m.put("capabilities", caps);
        return m;
    }

    @GetMapping("/formats/{format}")
    @Operation(summary = "Get format info", description = "Get details about a specific format handler")
    public ResponseEntity<DataFormatInfo> getFormatInfo(@PathVariable String format) {
        return formatRegistry.getFormatInfo(format)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/formats/by-extension/{extension}")
    @Operation(summary = "Get format by extension", description = "Get format handler for a file extension")
    public ResponseEntity<DataFormatInfo> getFormatByExtension(@PathVariable String extension) {
        return formatRegistry.getHandlerByExtension(extension)
            .map(h -> ResponseEntity.ok(h.getFormatInfo()))
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Storage Provider Discovery API ====================

    @GetMapping("/storage/providers")
    @Operation(summary = "List storage providers", description = "Get all available storage providers")
    public ResponseEntity<List<StorageProviderInfo>> getStorageProviders() {
        return ResponseEntity.ok(fileStorageService.getAvailableProviders());
    }

    @GetMapping("/storage/providers/active")
    @Operation(summary = "Get active storage provider", description = "Get the currently active storage provider")
    public ResponseEntity<Map<String, Object>> getActiveStorageProvider() {
        StorageProviderInfo info = fileStorageService.getActiveProviderInfo();
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "type", fileStorageService.getActiveProviderType(),
            "provider", info
        ));
    }

    /**
     * Ownership guard for DataSourceEntity. A null uploadedBy is treated as
     * legacy/unowned data — only admins can touch such records so that legacy
     * rows created before X-User-Id population don't become publicly readable.
     */
    private static void requireOwnerOrAdmin(DataSourceEntity entity, AuthUser user, String action) {
        if (user.isAdmin()) return;
        UUID owner = entity.getUploadedBy();
        if (owner == null || !owner.equals(user.id())) {
            throw new AccessDeniedException("Not authorized to " + action + " this data source");
        }
    }
}
