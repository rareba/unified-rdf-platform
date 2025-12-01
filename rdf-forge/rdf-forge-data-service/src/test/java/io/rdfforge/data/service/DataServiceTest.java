package io.rdfforge.data.service;

import io.rdfforge.data.entity.DataSourceEntity;
import io.rdfforge.data.entity.DataSourceEntity.DataFormat;
import io.rdfforge.data.repository.DataSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataService Tests")
class DataServiceTest {

    @Mock
    private DataSourceRepository dataSourceRepository;

    @Mock
    private FileStorageService fileStorageService;

    private DataService dataService;

    private UUID dataSourceId;
    private UUID projectId;
    private UUID userId;
    private DataSourceEntity sampleEntity;

    @BeforeEach
    void setUp() {
        dataService = new DataService(dataSourceRepository, fileStorageService);
        
        dataSourceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();

        sampleEntity = new DataSourceEntity();
        sampleEntity.setId(dataSourceId);
        sampleEntity.setProjectId(projectId);
        sampleEntity.setName("Test Data");
        sampleEntity.setOriginalFilename("test-data.csv");
        sampleEntity.setFormat(DataFormat.CSV);
        sampleEntity.setSizeBytes(1024L);
        sampleEntity.setStoragePath("/data/test-data.csv");
        sampleEntity.setRowCount(100L);
        sampleEntity.setColumnCount(5);
        sampleEntity.setUploadedBy(userId);
    }

    @Nested
    @DisplayName("getDataSource Tests")
    class GetDataSourceTests {

        @Test
        @DisplayName("Should return data source when found")
        void getDataSource_WhenFound_ReturnsDataSource() {
            when(dataSourceRepository.findById(dataSourceId)).thenReturn(Optional.of(sampleEntity));

            Optional<DataSourceEntity> result = dataService.getDataSource(dataSourceId);

            assertTrue(result.isPresent());
            assertEquals(dataSourceId, result.get().getId());
            assertEquals("Test Data", result.get().getName());
        }

        @Test
        @DisplayName("Should return empty when data source not found")
        void getDataSource_WhenNotFound_ReturnsEmpty() {
            when(dataSourceRepository.findById(dataSourceId)).thenReturn(Optional.empty());

            Optional<DataSourceEntity> result = dataService.getDataSource(dataSourceId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getDataSources Tests")
    class GetDataSourcesTests {

        @Test
        @DisplayName("Should return page of data sources with filters")
        void getDataSources_WithFilters_ReturnsPageOfDataSources() {
            Page<DataSourceEntity> page = new PageImpl<>(List.of(sampleEntity), PageRequest.of(0, 10), 1);
            when(dataSourceRepository.findWithFilters(eq(projectId), eq(DataFormat.CSV), eq("test"), any()))
                .thenReturn(page);

            Page<DataSourceEntity> result = dataService.getDataSources(projectId, DataFormat.CSV, "test", 0, 10);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Should return all data sources without filters")
        void getDataSources_WithoutFilters_ReturnsAll() {
            Page<DataSourceEntity> page = new PageImpl<>(List.of(sampleEntity), PageRequest.of(0, 10), 1);
            when(dataSourceRepository.findWithFilters(eq(projectId), isNull(), isNull(), any()))
                .thenReturn(page);

            Page<DataSourceEntity> result = dataService.getDataSources(projectId, null, null, 0, 10);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("uploadDataSource Tests")
    class UploadDataSourceTests {

        @Test
        @DisplayName("Should upload CSV file successfully")
        void uploadDataSource_CsvFile_Succeeds() throws IOException {
            String csvContent = "name,age,city\nJohn,30,NYC\nJane,25,LA";
            MockMultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv", csvContent.getBytes()
            );
            
            when(fileStorageService.uploadFile(any(MultipartFile.class), eq("data-sources")))
                .thenReturn("/storage/data.csv");
            when(dataSourceRepository.save(any(DataSourceEntity.class)))
                .thenAnswer(inv -> {
                    DataSourceEntity entity = inv.getArgument(0);
                    entity.setId(dataSourceId);
                    return entity;
                });

            DataSourceEntity result = dataService.uploadDataSource(file, "UTF-8", true, userId);

            assertNotNull(result);
            assertEquals("data", result.getName());
            assertEquals("data.csv", result.getOriginalFilename());
            assertEquals(DataFormat.CSV, result.getFormat());
            assertEquals(csvContent.length(), result.getSizeBytes());
        }

        @Test
        @DisplayName("Should upload TSV file and detect format")
        void uploadDataSource_TsvFile_DetectsFormat() throws IOException {
            String tsvContent = "name\tage\tcity\nJohn\t30\tNYC";
            MockMultipartFile file = new MockMultipartFile(
                "file", "data.tsv", "text/tab-separated-values", tsvContent.getBytes()
            );
            
            when(fileStorageService.uploadFile(any(), any())).thenReturn("/storage/data.tsv");
            when(dataSourceRepository.save(any(DataSourceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            DataSourceEntity result = dataService.uploadDataSource(file, "UTF-8", false, userId);

            assertEquals(DataFormat.TSV, result.getFormat());
        }

        @Test
        @DisplayName("Should upload JSON file and detect format")
        void uploadDataSource_JsonFile_DetectsFormat() throws IOException {
            String jsonContent = "[{\"name\": \"John\"}]";
            MockMultipartFile file = new MockMultipartFile(
                "file", "data.json", "application/json", jsonContent.getBytes()
            );
            
            when(fileStorageService.uploadFile(any(), any())).thenReturn("/storage/data.json");
            when(dataSourceRepository.save(any(DataSourceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            DataSourceEntity result = dataService.uploadDataSource(file, "UTF-8", false, userId);

            assertEquals(DataFormat.JSON, result.getFormat());
        }

        @Test
        @DisplayName("Should upload XLSX file and detect format")
        void uploadDataSource_XlsxFile_DetectsFormat() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                "file", "data.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
                new byte[100]
            );
            
            when(fileStorageService.uploadFile(any(), any())).thenReturn("/storage/data.xlsx");
            when(dataSourceRepository.save(any(DataSourceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            DataSourceEntity result = dataService.uploadDataSource(file, "UTF-8", false, userId);

            assertEquals(DataFormat.XLSX, result.getFormat());
        }

        @Test
        @DisplayName("Should analyze CSV file when analyze is true")
        void uploadDataSource_WithAnalyze_AnalyzesFile() throws IOException {
            String csvContent = "name,age,city\nJohn,30,NYC\nJane,25,LA";
            MockMultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv", csvContent.getBytes()
            );
            
            when(fileStorageService.uploadFile(any(), any())).thenReturn("/storage/data.csv");
            
            ArgumentCaptor<DataSourceEntity> captor = ArgumentCaptor.forClass(DataSourceEntity.class);
            when(dataSourceRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            dataService.uploadDataSource(file, "UTF-8", true, userId);

            DataSourceEntity savedEntity = captor.getValue();
            assertEquals(2L, savedEntity.getRowCount());
            assertEquals(3, savedEntity.getColumnCount());
            assertNotNull(savedEntity.getMetadata());
        }

        @Test
        @DisplayName("Should extract name from filename without extension")
        void uploadDataSource_ExtractsNameFromFilename() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                "file", "my-special-data.csv", "text/csv", "a,b\n1,2".getBytes()
            );
            
            when(fileStorageService.uploadFile(any(), any())).thenReturn("/storage/my-special-data.csv");
            when(dataSourceRepository.save(any(DataSourceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            DataSourceEntity result = dataService.uploadDataSource(file, "UTF-8", false, userId);

            assertEquals("my-special-data", result.getName());
        }

        @Test
        @DisplayName("Should handle null filename")
        void uploadDataSource_NullFilename_UsesUntitled() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                "file", null, "text/csv", "a,b\n1,2".getBytes()
            );
            
            when(fileStorageService.uploadFile(any(), any())).thenReturn("/storage/untitled.csv");
            when(dataSourceRepository.save(any(DataSourceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            DataSourceEntity result = dataService.uploadDataSource(file, "UTF-8", false, userId);

            assertEquals("Untitled", result.getName());
        }
    }

    @Nested
    @DisplayName("deleteDataSource Tests")
    class DeleteDataSourceTests {

        @Test
        @DisplayName("Should delete data source and file")
        void deleteDataSource_WhenExists_DeletesBoth() throws IOException {
            when(dataSourceRepository.findById(dataSourceId)).thenReturn(Optional.of(sampleEntity));

            dataService.deleteDataSource(dataSourceId);

            verify(fileStorageService).deleteFile(sampleEntity.getStoragePath());
            verify(dataSourceRepository).delete(sampleEntity);
        }

        @Test
        @DisplayName("Should not fail when data source not found")
        void deleteDataSource_WhenNotFound_DoesNothing() throws IOException {
            when(dataSourceRepository.findById(dataSourceId)).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> dataService.deleteDataSource(dataSourceId));

            verify(fileStorageService, never()).deleteFile(any());
            verify(dataSourceRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("previewDataSource Tests")
    class PreviewDataSourceTests {

        @Test
        @DisplayName("Should preview CSV data source")
        void previewDataSource_Csv_ReturnsPreview() throws IOException {
            String csvContent = "name,age,city\nJohn,30,NYC\nJane,25,LA\nBob,35,SF";
            InputStream csvStream = new ByteArrayInputStream(csvContent.getBytes());
            
            when(dataSourceRepository.findById(dataSourceId)).thenReturn(Optional.of(sampleEntity));
            when(fileStorageService.downloadFile(sampleEntity.getStoragePath())).thenReturn(csvStream);

            Map<String, Object> result = dataService.previewDataSource(dataSourceId, 2, 0);

            assertNotNull(result);
            assertTrue(result.containsKey("columns"));
            assertTrue(result.containsKey("data"));
            assertTrue(result.containsKey("totalRows"));
            
            @SuppressWarnings("unchecked")
            List<String> columns = (List<String>) result.get("columns");
            assertEquals(3, columns.size());
            assertEquals("name", columns.get(0));
        }

        @Test
        @DisplayName("Should preview TSV data source")
        void previewDataSource_Tsv_ReturnsPreview() throws IOException {
            String tsvContent = "name\tage\tcity\nJohn\t30\tNYC";
            InputStream tsvStream = new ByteArrayInputStream(tsvContent.getBytes());
            
            DataSourceEntity tsvEntity = new DataSourceEntity();
            tsvEntity.setId(dataSourceId);
            tsvEntity.setFormat(DataFormat.TSV);
            tsvEntity.setStoragePath("/data/test.tsv");
            
            when(dataSourceRepository.findById(dataSourceId)).thenReturn(Optional.of(tsvEntity));
            when(fileStorageService.downloadFile(any())).thenReturn(tsvStream);

            Map<String, Object> result = dataService.previewDataSource(dataSourceId, 10, 0);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should throw exception when data source not found")
        void previewDataSource_WhenNotFound_ThrowsException() {
            when(dataSourceRepository.findById(dataSourceId)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> 
                dataService.previewDataSource(dataSourceId, 10, 0)
            );
        }

        @Test
        @DisplayName("Should return error for unsupported format")
        void previewDataSource_UnsupportedFormat_ReturnsError() throws IOException {
            DataSourceEntity parquetEntity = new DataSourceEntity();
            parquetEntity.setId(dataSourceId);
            parquetEntity.setFormat(DataFormat.PARQUET);
            parquetEntity.setStoragePath("/data/test.parquet");
            
            when(dataSourceRepository.findById(dataSourceId)).thenReturn(Optional.of(parquetEntity));
            when(fileStorageService.downloadFile(any())).thenReturn(new ByteArrayInputStream(new byte[0]));

            Map<String, Object> result = dataService.previewDataSource(dataSourceId, 10, 0);

            assertTrue(result.containsKey("error"));
        }
    }

    @Nested
    @DisplayName("downloadDataSource Tests")
    class DownloadDataSourceTests {

        @Test
        @DisplayName("Should download data source")
        void downloadDataSource_WhenExists_ReturnsInputStream() throws IOException {
            InputStream expectedStream = new ByteArrayInputStream("data".getBytes());
            
            when(dataSourceRepository.findById(dataSourceId)).thenReturn(Optional.of(sampleEntity));
            when(fileStorageService.downloadFile(sampleEntity.getStoragePath())).thenReturn(expectedStream);

            InputStream result = dataService.downloadDataSource(dataSourceId);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should throw exception when data source not found")
        void downloadDataSource_WhenNotFound_ThrowsException() {
            when(dataSourceRepository.findById(dataSourceId)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> 
                dataService.downloadDataSource(dataSourceId)
            );
        }
    }

    @Nested
    @DisplayName("Column Type Detection Tests")
    class ColumnTypeDetectionTests {

        @Test
        @DisplayName("Should detect integer columns")
        void analyzeCsv_WithIntegers_DetectsIntegerType() throws IOException {
            String csvContent = "value\n100\n200\n300";
            MockMultipartFile file = new MockMultipartFile(
                "file", "integers.csv", "text/csv", csvContent.getBytes()
            );
            
            when(fileStorageService.uploadFile(any(), any())).thenReturn("/storage/integers.csv");
            
            ArgumentCaptor<DataSourceEntity> captor = ArgumentCaptor.forClass(DataSourceEntity.class);
            when(dataSourceRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            dataService.uploadDataSource(file, "UTF-8", true, userId);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> columns = (List<Map<String, Object>>) 
                captor.getValue().getMetadata().get("columns");
            assertEquals("integer", columns.get(0).get("type"));
        }

        @Test
        @DisplayName("Should detect date columns")
        void analyzeCsv_WithDates_DetectsDateType() throws IOException {
            String csvContent = "date\n2024-01-01\n2024-02-15\n2024-03-30";
            MockMultipartFile file = new MockMultipartFile(
                "file", "dates.csv", "text/csv", csvContent.getBytes()
            );
            
            when(fileStorageService.uploadFile(any(), any())).thenReturn("/storage/dates.csv");
            
            ArgumentCaptor<DataSourceEntity> captor = ArgumentCaptor.forClass(DataSourceEntity.class);
            when(dataSourceRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            dataService.uploadDataSource(file, "UTF-8", true, userId);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> columns = (List<Map<String, Object>>) 
                captor.getValue().getMetadata().get("columns");
            assertEquals("date", columns.get(0).get("type"));
        }

        @Test
        @DisplayName("Should detect boolean columns")
        void analyzeCsv_WithBooleans_DetectsBooleanType() throws IOException {
            String csvContent = "flag\ntrue\nfalse\nTrue";
            MockMultipartFile file = new MockMultipartFile(
                "file", "booleans.csv", "text/csv", csvContent.getBytes()
            );
            
            when(fileStorageService.uploadFile(any(), any())).thenReturn("/storage/booleans.csv");
            
            ArgumentCaptor<DataSourceEntity> captor = ArgumentCaptor.forClass(DataSourceEntity.class);
            when(dataSourceRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            dataService.uploadDataSource(file, "UTF-8", true, userId);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> columns = (List<Map<String, Object>>) 
                captor.getValue().getMetadata().get("columns");
            assertEquals("boolean", columns.get(0).get("type"));
        }
    }
}
