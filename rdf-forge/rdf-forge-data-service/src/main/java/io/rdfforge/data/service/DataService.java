package io.rdfforge.data.service;

import io.rdfforge.common.exception.RdfForgeException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.data.entity.DataSourceEntity;
import io.rdfforge.data.entity.DataSourceEntity.DataFormat;
import io.rdfforge.data.repository.DataSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
@Slf4j
public class DataService {

    // Maximum file size: 100MB
    private static final long MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024;

    // Allowed MIME types for upload
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "text/csv",
        "text/plain",
        "text/tab-separated-values",
        "text/xml",
        "application/xml",
        "application/json",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/octet-stream" // For binary files that need content sniffing
    );

    // File extensions to MIME type mapping for validation. Parquet is intentionally
    // absent — the format is known to the registry as a stub only, and uploads must
    // be rejected at this layer rather than silently accepted and then broken later.
    private static final Map<String, String> EXTENSION_TO_MIME = Map.of(
        ".csv", "text/csv",
        ".tsv", "text/tab-separated-values",
        ".json", "application/json",
        ".xls", "application/vnd.ms-excel",
        ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ".xml", "application/xml",
        ".txt", "text/plain"
    );

    private final DataSourceRepository repository;
    private final FileStorageService storageService;

    @Value("${data.virus-scan.enabled:false}")
    private boolean virusScanEnabled;

    public DataService(DataSourceRepository repository, FileStorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }
    
    @Transactional(readOnly = true)
    public Page<DataSourceEntity> getDataSources(UUID projectId, DataFormat format, String search, int page, int size) {
        String formatStr = format != null ? format.name() : null;
        return repository.findWithFilters(projectId, formatStr, search, PageRequest.of(page, size));
    }
    
    @Transactional(readOnly = true)
    public Optional<DataSourceEntity> getDataSource(UUID id) {
        return repository.findById(id);
    }
    
    public DataSourceEntity uploadDataSource(MultipartFile file, String encoding, boolean analyze, UUID userId) throws IOException {
        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                String.format("File size exceeds maximum allowed size of %d MB", MAX_FILE_SIZE_BYTES / (1024 * 1024)));
        }

        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType != null && !isAllowedMimeType(contentType, file.getOriginalFilename())) {
            throw new IllegalArgumentException(
                "File type '" + contentType + "' is not allowed. Allowed types: CSV, TSV, JSON, Excel, TXT");
        }

        // Virus scan placeholder (for future implementation)
        if (virusScanEnabled) {
            performVirusScan(file);
        }

        DataFormat format = detectFormat(file.getOriginalFilename());

        // Parquet is declared in the DataFormat enum for forward compatibility but
        // is not implemented. Reject the upload loudly so the user is not lied to.
        if (format == DataFormat.PARQUET) {
            throw new IllegalArgumentException(
                "Parquet uploads are not supported yet. See docs/TODO_PARQUET.md for status.");
        }

        Path tempFile = null;

        try {
            // Create temp file for safe processing
            tempFile = Files.createTempFile("upload-", "-" + file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            // Validate the temp file size matches expected size
            long actualSize = Files.size(tempFile);
            if (actualSize > MAX_FILE_SIZE_BYTES) {
                throw new IllegalArgumentException("File size validation failed after upload");
            }

            // Upload to storage
            String storagePath = storageService.uploadFile(file, "data-sources");

            DataSourceEntity entity = new DataSourceEntity();
            entity.setName(getNameFromFilename(file.getOriginalFilename()));
            entity.setOriginalFilename(sanitizeFilename(file.getOriginalFilename()));
            entity.setFormat(format);
            entity.setSizeBytes(file.getSize());
            entity.setStoragePath(storagePath);
            entity.setUploadedBy(userId);
            entity.setUploadedAt(Instant.now());

            if (analyze) {
                Map<String, Object> analysisResult = analyzeFile(file, format, encoding);
                entity.setRowCount((Long) analysisResult.get("rowCount"));
                entity.setColumnCount((Integer) analysisResult.get("columnCount"));
                entity.setMetadata(analysisResult);
            }

            log.info("Uploaded data source: {} (format: {}, size: {} bytes) by user: {}",
                entity.getName(), format, file.getSize(), userId);

            return repository.save(entity);
        } finally {
            // Clean up temp file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", tempFile, e);
                }
            }
        }
    }

    /**
     * Check if MIME type is allowed for upload.
     */
    private boolean isAllowedMimeType(String contentType, String filename) {
        // Check against allowed types
        if (ALLOWED_MIME_TYPES.contains(contentType)) {
            return true;
        }

        // Additional validation based on file extension
        if (filename != null) {
            String lowerFilename = filename.toLowerCase();
            for (Map.Entry<String, String> entry : EXTENSION_TO_MIME.entrySet()) {
                if (lowerFilename.endsWith(entry.getKey())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Placeholder for virus scanning functionality.
     * SECURITY WARNING: This method currently does NOT scan files for malware.
     * All file uploads should be considered potentially unsafe until this is implemented.
     *
     * TODO: Implement virus scanning integration (Issue #XXX)
     * See: https://github.com/your-org/rdf-forge/issues/XXX
     */
    private void performVirusScan(MultipartFile file) {
        if (virusScanEnabled) {
            log.error("SECURITY: Virus scanning is enabled in config but NOT IMPLEMENTED! " +
                      "File '{}' was uploaded without malware scanning. " +
                      "This is a known security risk tracked in Issue #XXX",
                      file.getOriginalFilename());
            // TODO: Implement actual virus scanning when service is available
            // For now, we log the error but still allow the upload
        } else {
            log.warn("SECURITY: Virus scanning is DISABLED. File '{}' was uploaded without malware scanning. " +
                     "Enable data.virus-scan.enabled in configuration and implement the scanner.",
                     file.getOriginalFilename());
        }
    }

    /**
     * Sanitize filename to prevent path traversal attacks.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unnamed";
        }
        // Remove path components, keeping only the filename
        String sanitized = filename.replaceAll(".*[/\\\\]", "");
        // Remove any control characters
        sanitized = sanitized.replaceAll("[\\x00-\\x1f\\x7f]", "");
        // Limit length
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }
        return sanitized;
    }
    
    public void deleteDataSource(UUID id) throws IOException {
        repository.findById(id).ifPresent(entity -> {
            try {
                storageService.deleteFile(entity.getStoragePath());
            } catch (IOException e) {
                throw new RdfForgeException("DATA_DELETE_ERROR", "Failed to delete file: " + entity.getStoragePath(), e);
            }
            repository.delete(entity);
        });
    }
    
    @Transactional(readOnly = true)
    public Map<String, Object> previewDataSource(UUID id, int rows, int offset) throws IOException {
        DataSourceEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DataSource", id.toString()));

        try (InputStream inputStream = storageService.downloadFile(entity.getStoragePath())) {
            return switch (entity.getFormat()) {
                case CSV, TSV -> previewCsv(inputStream, entity.getFormat() == DataFormat.TSV ? "\t" : ",", rows, offset);
                case JSON -> previewJson(inputStream, rows, offset);
                default -> Map.of("error", "Preview not supported for format: " + entity.getFormat());
            };
        }
    }
    
    @Transactional(readOnly = true)
    public InputStream downloadDataSource(UUID id) throws IOException {
        DataSourceEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DataSource", id.toString()));

        return storageService.downloadFile(entity.getStoragePath());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeDataSource(UUID id) throws IOException {
        DataSourceEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DataSource", id.toString()));

        // If metadata already contains column info from upload analysis, use it
        Map<String, Object> metadata = entity.getMetadata();
        if (metadata != null && metadata.containsKey("columns")) {
            List<Map<String, Object>> columns = (List<Map<String, Object>>) metadata.get("columns");
            Long rowCount = entity.getRowCount();
            return Map.of(
                "columns", columns,
                "rowCount", rowCount != null ? rowCount : 0L
            );
        }

        // Otherwise, re-analyze the file
        Map<String, Object> analysis;
        try (InputStream inputStream = storageService.downloadFile(entity.getStoragePath())) {
            analysis = switch (entity.getFormat()) {
                case CSV, TSV -> analyzeCsv(inputStream, entity.getFormat() == DataFormat.TSV ? "\t" : ",", "UTF-8");
                case JSON -> analyzeJson(inputStream);
                default -> Map.of("columnCount", 0, "rowCount", 0L, "columns", List.of());
            };
        }

        // Update entity with analysis results
        entity.setMetadata(analysis);
        if (analysis.containsKey("rowCount")) {
            entity.setRowCount(((Number) analysis.get("rowCount")).longValue());
        }
        if (analysis.containsKey("columnCount")) {
            entity.setColumnCount((Integer) analysis.get("columnCount"));
        }
        repository.save(entity);

        List<Map<String, Object>> columns = analysis.containsKey("columns")
            ? (List<Map<String, Object>>) analysis.get("columns")
            : List.of();
        Long rowCount = analysis.containsKey("rowCount")
            ? ((Number) analysis.get("rowCount")).longValue()
            : 0L;

        return Map.of(
            "columns", columns,
            "rowCount", rowCount
        );
    }
    
    private DataFormat detectFormat(String filename) {
        if (filename == null) return DataFormat.CSV;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv")) return DataFormat.CSV;
        if (lower.endsWith(".tsv")) return DataFormat.TSV;
        if (lower.endsWith(".json")) return DataFormat.JSON;
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return DataFormat.XLSX;
        if (lower.endsWith(".parquet")) return DataFormat.PARQUET;
        if (lower.endsWith(".xml")) return DataFormat.XML;
        return DataFormat.CSV;
    }
    
    private String getNameFromFilename(String filename) {
        if (filename == null) return "Untitled";
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }
    
    private Map<String, Object> analyzeFile(MultipartFile file, DataFormat format, String encoding) throws IOException {
        return switch (format) {
            case CSV, TSV -> analyzeCsv(file.getInputStream(), format == DataFormat.TSV ? "\t" : ",", encoding);
            case JSON -> analyzeJson(file.getInputStream());
            default -> Map.of("columnCount", 0, "rowCount", 0L);
        };
    }
    
    private Map<String, Object> analyzeCsv(InputStream input, String delimiter, String encoding) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, encoding != null ? encoding : "UTF-8"));
        
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return Map.of("columnCount", 0, "rowCount", 0L, "columns", List.of());
        }
        
        String[] headers = headerLine.split(delimiter);
        List<Map<String, Object>> columns = new ArrayList<>();
        Map<Integer, Set<String>> uniqueValues = new HashMap<>();
        Map<Integer, Integer> nullCounts = new HashMap<>();
        Map<Integer, List<String>> sampleValues = new HashMap<>();
        
        for (int i = 0; i < headers.length; i++) {
            uniqueValues.put(i, new HashSet<>());
            nullCounts.put(i, 0);
            sampleValues.put(i, new ArrayList<>());
        }
        
        long rowCount = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            rowCount++;
            String[] values = line.split(delimiter, -1);
            for (int i = 0; i < Math.min(values.length, headers.length); i++) {
                String value = values[i].trim();
                if (value.isEmpty()) {
                    nullCounts.merge(i, 1, Integer::sum);
                } else {
                    uniqueValues.get(i).add(value);
                    if (sampleValues.get(i).size() < 5) {
                        sampleValues.get(i).add(value);
                    }
                }
            }
        }
        
        for (int i = 0; i < headers.length; i++) {
            String detectedType = detectColumnType(sampleValues.get(i));
            columns.add(Map.of(
                "name", headers[i].replace("\"", ""),
                "type", detectedType,
                "nullCount", nullCounts.getOrDefault(i, 0),
                "nullPercent", rowCount > 0 ? Math.round(nullCounts.getOrDefault(i, 0) * 100.0 / rowCount) : 0,
                "uniqueCount", uniqueValues.get(i).size(),
                "sample", sampleValues.getOrDefault(i, List.of())
            ));
        }
        
        return Map.of(
            "columnCount", headers.length,
            "rowCount", rowCount,
            "columns", columns
        );
    }
    
    private String detectColumnType(List<String> samples) {
        if (samples.isEmpty()) return "string";
        
        boolean allIntegers = samples.stream().allMatch(s -> s.matches("-?\\d+"));
        if (allIntegers) return "integer";
        
        boolean allDecimals = samples.stream().allMatch(s -> s.matches("-?\\d+\\.\\d+"));
        if (allDecimals) return "decimal";
        
        boolean allDates = samples.stream().allMatch(s -> s.matches("\\d{4}-\\d{2}-\\d{2}"));
        if (allDates) return "date";
        
        boolean allBooleans = samples.stream().allMatch(s -> 
            s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"));
        if (allBooleans) return "boolean";
        
        return "string";
    }
    
    private Map<String, Object> analyzeJson(InputStream input) throws IOException {
        return Map.of("columnCount", 0, "rowCount", 0L);
    }
    
    private Map<String, Object> previewCsv(InputStream input, String delimiter, int maxRows, int offset) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return Map.of("columns", List.of(), "data", List.of(), "totalRows", 0);
        }
        
        String[] headers = headerLine.split(delimiter);
        List<String> columns = Arrays.stream(headers).map(h -> h.replace("\"", "")).toList();
        
        for (int i = 0; i < offset && reader.readLine() != null; i++) {}
        
        List<Map<String, Object>> data = new ArrayList<>();
        String line;
        int count = 0;
        long totalRows = 0;
        
        while ((line = reader.readLine()) != null) {
            totalRows++;
            if (count < maxRows) {
                String[] values = line.split(delimiter, -1);
                Map<String, Object> row = new HashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    String value = i < values.length ? values[i].replace("\"", "") : null;
                    row.put(columns.get(i), value == null || value.isEmpty() ? null : value);
                }
                data.add(row);
                count++;
            }
        }
        
        return Map.of("columns", columns, "data", data, "totalRows", totalRows + offset);
    }
    
    private Map<String, Object> previewJson(InputStream input, int maxRows, int offset) throws IOException {
        return Map.of("columns", List.of(), "data", List.of(), "totalRows", 0);
    }
}
