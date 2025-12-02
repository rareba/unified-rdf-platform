package io.rdfforge.data.service;

import io.rdfforge.data.entity.DataSourceEntity;
import io.rdfforge.data.entity.DataSourceEntity.DataFormat;
import io.rdfforge.data.repository.DataSourceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

@Service
@Transactional
public class DataService {
    
    private final DataSourceRepository repository;
    private final FileStorageService storageService;
    
    public DataService(DataSourceRepository repository, FileStorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }
    
    public Page<DataSourceEntity> getDataSources(UUID projectId, DataFormat format, String search, int page, int size) {
        String formatStr = format != null ? format.name() : null;
        return repository.findWithFilters(projectId, formatStr, search, PageRequest.of(page, size));
    }
    
    public Optional<DataSourceEntity> getDataSource(UUID id) {
        return repository.findById(id);
    }
    
    public DataSourceEntity uploadDataSource(MultipartFile file, String encoding, boolean analyze, UUID userId) throws IOException {
        DataFormat format = detectFormat(file.getOriginalFilename());
        String storagePath = storageService.uploadFile(file, "data-sources");
        
        DataSourceEntity entity = new DataSourceEntity();
        entity.setName(getNameFromFilename(file.getOriginalFilename()));
        entity.setOriginalFilename(file.getOriginalFilename());
        entity.setFormat(format);
        entity.setSizeBytes(file.getSize());
        entity.setStoragePath(storagePath);
        entity.setUploadedBy(userId);
        
        if (analyze) {
            Map<String, Object> analysisResult = analyzeFile(file, format, encoding);
            entity.setRowCount((Long) analysisResult.get("rowCount"));
            entity.setColumnCount((Integer) analysisResult.get("columnCount"));
            entity.setMetadata(analysisResult);
        }
        
        return repository.save(entity);
    }
    
    public void deleteDataSource(UUID id) throws IOException {
        repository.findById(id).ifPresent(entity -> {
            try {
                storageService.deleteFile(entity.getStoragePath());
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete file", e);
            }
            repository.delete(entity);
        });
    }
    
    public Map<String, Object> previewDataSource(UUID id, int rows, int offset) throws IOException {
        DataSourceEntity entity = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Data source not found: " + id));
        
        InputStream inputStream = storageService.downloadFile(entity.getStoragePath());
        
        return switch (entity.getFormat()) {
            case CSV, TSV -> previewCsv(inputStream, entity.getFormat() == DataFormat.TSV ? "\t" : ",", rows, offset);
            case JSON -> previewJson(inputStream, rows, offset);
            default -> Map.of("error", "Preview not supported for format: " + entity.getFormat());
        };
    }
    
    public InputStream downloadDataSource(UUID id) throws IOException {
        DataSourceEntity entity = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Data source not found: " + id));
        
        return storageService.downloadFile(entity.getStoragePath());
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
                    row.put(columns.get(i), value.isEmpty() ? null : value);
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
