package io.rdfforge.data.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "data_sources")
public class DataSourceEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "project_id")
    private UUID projectId;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "original_filename")
    private String originalFilename;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataFormat format;
    
    @Column(name = "size_bytes")
    private Long sizeBytes;
    
    @Column(name = "row_count")
    private Long rowCount;
    
    @Column(name = "column_count")
    private Integer columnCount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type")
    private StorageType storageType = StorageType.S3;
    
    @Column(name = "storage_path")
    private String storagePath;
    
    @Column(columnDefinition = "jsonb")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> metadata;
    
    @Column(name = "uploaded_by")
    private UUID uploadedBy;
    
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();
    
    public enum DataFormat {
        CSV, JSON, XLSX, PARQUET, XML, TSV
    }
    
    public enum StorageType {
        S3, LOCAL, URL
    }
    
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    
    public DataFormat getFormat() { return format; }
    public void setFormat(DataFormat format) { this.format = format; }
    
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    
    public Integer getColumnCount() { return columnCount; }
    public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }
    
    public StorageType getStorageType() { return storageType; }
    public void setStorageType(StorageType storageType) { this.storageType = storageType; }
    
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }
    
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
}
