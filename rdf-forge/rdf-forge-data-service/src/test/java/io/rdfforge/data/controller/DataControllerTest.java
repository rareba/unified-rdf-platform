package io.rdfforge.data.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rdfforge.data.config.TestSecurityConfig;
import io.rdfforge.data.entity.DataSourceEntity;
import io.rdfforge.data.entity.DataSourceEntity.DataFormat;
import io.rdfforge.data.format.DataFormatInfo;
import io.rdfforge.data.format.DataFormatRegistry;
import io.rdfforge.data.service.DataService;
import io.rdfforge.data.service.FileStorageService;
import io.rdfforge.data.storage.StorageProviderInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for DataController.
 *
 * Security is disabled via TestSecurityConfig. DataService, DataFormatRegistry,
 * and FileStorageService are all fully mocked. Tests cover every endpoint,
 * including multipart upload and streaming download.
 */
@WebMvcTest(DataController.class)
@Import(TestSecurityConfig.class)
@DisplayName("DataController Tests")
class DataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DataService dataService;

    @MockBean
    private DataFormatRegistry formatRegistry;

    @MockBean
    private FileStorageService fileStorageService;

    private UUID dataSourceId;
    private DataSourceEntity sampleEntity;

    @BeforeEach
    void setUp() {
        dataSourceId = UUID.randomUUID();

        sampleEntity = new DataSourceEntity();
        sampleEntity.setId(dataSourceId);
        sampleEntity.setName("Test Data");
        sampleEntity.setOriginalFilename("test.csv");
        sampleEntity.setFormat(DataFormat.CSV);
        sampleEntity.setSizeBytes(1024L);
        sampleEntity.setStoragePath("/data/test.csv");
        sampleEntity.setRowCount(100L);
        sampleEntity.setColumnCount(3);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/data
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/data — list data sources")
    class ListDataSourcesTests {

        @Test
        @DisplayName("Should return 200 with a page of data sources")
        void listData_NoFilters_Returns200WithPage() throws Exception {
            Page<DataSourceEntity> page = new PageImpl<>(List.of(sampleEntity));
            when(dataService.getDataSources(isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(page);

            mockMvc.perform(get("/api/v1/data"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(dataSourceId.toString())))
                .andExpect(jsonPath("$.content[0].name", is("Test Data")));
        }

        @Test
        @DisplayName("Should return 200 with empty page when no data sources exist")
        void listData_NoResults_Returns200WithEmptyPage() throws Exception {
            Page<DataSourceEntity> empty = new PageImpl<>(List.of());
            when(dataService.getDataSources(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(empty);

            mockMvc.perform(get("/api/v1/data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
        }

        @Test
        @DisplayName("Should pass projectId filter to service when provided")
        void listData_WithProjectId_PassesFilterToService() throws Exception {
            UUID projectId = UUID.randomUUID();
            Page<DataSourceEntity> page = new PageImpl<>(List.of(sampleEntity));
            when(dataService.getDataSources(eq(projectId), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

            mockMvc.perform(get("/api/v1/data").param("projectId", projectId.toString()))
                .andExpect(status().isOk());

            verify(dataService).getDataSources(eq(projectId), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should pass format filter to service when provided")
        void listData_WithFormatFilter_PassesFormatToService() throws Exception {
            Page<DataSourceEntity> page = new PageImpl<>(List.of(sampleEntity));
            when(dataService.getDataSources(any(), eq(DataFormat.CSV), any(), anyInt(), anyInt()))
                .thenReturn(page);

            mockMvc.perform(get("/api/v1/data").param("format", "CSV"))
                .andExpect(status().isOk());

            verify(dataService).getDataSources(any(), eq(DataFormat.CSV), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should pass search string to service when provided")
        void listData_WithSearch_PassesSearchToService() throws Exception {
            Page<DataSourceEntity> page = new PageImpl<>(List.of());
            when(dataService.getDataSources(any(), any(), eq("sales"), anyInt(), anyInt()))
                .thenReturn(page);

            mockMvc.perform(get("/api/v1/data").param("search", "sales"))
                .andExpect(status().isOk());

            verify(dataService).getDataSources(any(), any(), eq("sales"), anyInt(), anyInt());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/data/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/data/{id} — get data source by ID")
    class GetDataSourceTests {

        @Test
        @DisplayName("Should return 200 with data source body when it exists")
        void getDataSource_Existing_Returns200WithBody() throws Exception {
            when(dataService.getDataSource(dataSourceId)).thenReturn(Optional.of(sampleEntity));

            mockMvc.perform(get("/api/v1/data/{id}", dataSourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(dataSourceId.toString())))
                .andExpect(jsonPath("$.name", is("Test Data")))
                .andExpect(jsonPath("$.format", is("CSV")));
        }

        @Test
        @DisplayName("Should return 404 when data source does not exist")
        void getDataSource_NotFound_Returns404() throws Exception {
            when(dataService.getDataSource(dataSourceId)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/data/{id}", dataSourceId))
                .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/data/upload
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/data/upload — upload data source")
    class UploadDataSourceTests {

        @Test
        @DisplayName("Should return 200 with created entity on successful CSV upload")
        void uploadDataSource_CsvFile_Returns200WithEntity() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv",
                "name,age\nAlice,30\nBob,25\n".getBytes());
            when(dataService.uploadDataSource(any(), eq("UTF-8"), eq(true), isNull()))
                .thenReturn(sampleEntity);

            mockMvc.perform(multipart("/api/v1/data/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(dataSourceId.toString())))
                .andExpect(jsonPath("$.name", is("Test Data")));
        }

        @Test
        @DisplayName("Should pass encoding parameter to service")
        void uploadDataSource_CustomEncoding_PassesEncodingToService() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv", "col\nval\n".getBytes());
            when(dataService.uploadDataSource(any(), eq("ISO-8859-1"), anyBoolean(), any()))
                .thenReturn(sampleEntity);

            mockMvc.perform(multipart("/api/v1/data/upload")
                    .file(file)
                    .param("encoding", "ISO-8859-1"))
                .andExpect(status().isOk());

            verify(dataService).uploadDataSource(any(), eq("ISO-8859-1"), anyBoolean(), any());
        }

        @Test
        @DisplayName("Should default analyze to true when not specified")
        void uploadDataSource_DefaultAnalyze_PassesTrueToService() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv", "col\nval\n".getBytes());
            when(dataService.uploadDataSource(any(), any(), eq(true), any()))
                .thenReturn(sampleEntity);

            mockMvc.perform(multipart("/api/v1/data/upload").file(file))
                .andExpect(status().isOk());

            verify(dataService).uploadDataSource(any(), any(), eq(true), any());
        }

        @Test
        @DisplayName("Should pass analyze=false to service when explicitly set")
        void uploadDataSource_AnalyzeFalse_PassesFalseToService() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv", "col\nval\n".getBytes());
            when(dataService.uploadDataSource(any(), any(), eq(false), any()))
                .thenReturn(sampleEntity);

            mockMvc.perform(multipart("/api/v1/data/upload")
                    .file(file)
                    .param("analyze", "false"))
                .andExpect(status().isOk());

            verify(dataService).uploadDataSource(any(), any(), eq(false), any());
        }

        @Test
        @DisplayName("Should return 200 for JSON file upload")
        void uploadDataSource_JsonFile_Returns200() throws Exception {
            DataSourceEntity jsonEntity = new DataSourceEntity();
            jsonEntity.setId(UUID.randomUUID());
            jsonEntity.setName("data");
            jsonEntity.setFormat(DataFormat.JSON);
            jsonEntity.setOriginalFilename("data.json");

            MockMultipartFile file = new MockMultipartFile(
                "file", "data.json", "application/json", "[{}]".getBytes());
            when(dataService.uploadDataSource(any(), any(), anyBoolean(), any()))
                .thenReturn(jsonEntity);

            mockMvc.perform(multipart("/api/v1/data/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format", is("JSON")));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/data/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/v1/data/{id} — delete data source")
    class DeleteDataSourceTests {

        @Test
        @DisplayName("Should return 204 No Content on successful deletion")
        void deleteDataSource_Existing_Returns204() throws Exception {
            doNothing().when(dataService).deleteDataSource(dataSourceId);

            mockMvc.perform(delete("/api/v1/data/{id}", dataSourceId))
                .andExpect(status().isNoContent());

            verify(dataService).deleteDataSource(dataSourceId);
        }

        @Test
        @DisplayName("Should return 204 even when data source does not exist (idempotent)")
        void deleteDataSource_NotFound_Returns204() throws Exception {
            // The controller does not map ResourceNotFoundException to 404 on DELETE;
            // the service already handles the no-op case internally.
            doNothing().when(dataService).deleteDataSource(dataSourceId);

            mockMvc.perform(delete("/api/v1/data/{id}", dataSourceId))
                .andExpect(status().isNoContent());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/data/{id}/preview
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/data/{id}/preview — preview data source")
    class PreviewDataSourceTests {

        @Test
        @DisplayName("Should return 200 with preview payload when data source exists")
        void previewDataSource_Existing_Returns200WithPreview() throws Exception {
            Map<String, Object> preview = Map.of(
                "columns", List.of("name", "age"),
                "data", List.of(Map.of("name", "Alice", "age", "30")),
                "totalRows", 1L
            );
            when(dataService.previewDataSource(dataSourceId, 100, 0)).thenReturn(preview);

            mockMvc.perform(get("/api/v1/data/{id}/preview", dataSourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns", hasSize(2)))
                .andExpect(jsonPath("$.totalRows", is(1)));
        }

        @Test
        @DisplayName("Should honour custom rows and offset parameters")
        void previewDataSource_CustomRowsOffset_PassesToService() throws Exception {
            Map<String, Object> preview = Map.of("columns", List.of(), "data", List.of(),
                "totalRows", 0L);
            when(dataService.previewDataSource(dataSourceId, 25, 50)).thenReturn(preview);

            mockMvc.perform(get("/api/v1/data/{id}/preview", dataSourceId)
                    .param("rows", "25")
                    .param("offset", "50"))
                .andExpect(status().isOk());

            verify(dataService).previewDataSource(dataSourceId, 25, 50);
        }

        @Test
        @DisplayName("Should return 200 with error key when format is unsupported")
        void previewDataSource_UnsupportedFormat_Returns200WithErrorKey() throws Exception {
            Map<String, Object> errorPreview = Map.of("error", "Unsupported format");
            when(dataService.previewDataSource(dataSourceId, 100, 0)).thenReturn(errorPreview);

            mockMvc.perform(get("/api/v1/data/{id}/preview", dataSourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error", notNullValue()));
        }

        @Test
        @DisplayName("Should propagate exception as 500 when service throws RuntimeException")
        void previewDataSource_ServiceThrows_Returns500() throws Exception {
            when(dataService.previewDataSource(dataSourceId, 100, 0))
                .thenThrow(new RuntimeException("Data source not found"));

            mockMvc.perform(get("/api/v1/data/{id}/preview", dataSourceId))
                .andExpect(status().is5xxServerError());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/data/{id}/download
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/data/{id}/download — download data source")
    class DownloadDataSourceTests {

        @Test
        @DisplayName("Should return 200 with Content-Disposition header and octet-stream body")
        void downloadDataSource_Existing_Returns200WithFile() throws Exception {
            byte[] content = "name,age\nAlice,30\n".getBytes();
            sampleEntity.setSizeBytes((long) content.length);
            when(dataService.getDataSource(dataSourceId)).thenReturn(Optional.of(sampleEntity));
            when(dataService.downloadDataSource(dataSourceId))
                .thenReturn(new ByteArrayInputStream(content));

            mockMvc.perform(get("/api/v1/data/{id}/download", dataSourceId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                    containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                    containsString("test.csv")))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
        }

        @Test
        @DisplayName("Should return 500 when data source does not exist for download")
        void downloadDataSource_NotFound_Returns500() throws Exception {
            when(dataService.getDataSource(dataSourceId)).thenReturn(Optional.empty());

            // The controller throws RuntimeException when entity is not found —
            // this maps to 500 without a specific exception handler.
            mockMvc.perform(get("/api/v1/data/{id}/download", dataSourceId))
                .andExpect(status().is5xxServerError());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/data/{id}/analyze
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/data/{id}/analyze — analyze data source")
    class AnalyzeDataSourceTests {

        @Test
        @DisplayName("Should return 200 with analysis result map")
        void analyzeDataSource_Existing_Returns200WithAnalysis() throws Exception {
            Map<String, Object> analysis = Map.of(
                "rowCount", 100L,
                "columns", List.of(Map.of("name", "age", "type", "integer"))
            );
            when(dataService.analyzeDataSource(dataSourceId)).thenReturn(analysis);

            mockMvc.perform(post("/api/v1/data/{id}/analyze", dataSourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount", is(100)));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/data/detect-format
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/data/detect-format — detect format")
    class DetectFormatTests {

        @Test
        @DisplayName("Should return 200 with detected CSV format for a .csv file")
        void detectFormat_CsvFile_ReturnsCsvFormat() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv", "col\nval\n".getBytes());
            when(formatRegistry.detectFormat("data.csv")).thenReturn(Optional.of("csv"));
            when(formatRegistry.getFormatInfo("csv")).thenReturn(Optional.empty());

            mockMvc.perform(multipart("/api/v1/data/detect-format").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format", is("csv")))
                .andExpect(jsonPath("$.encoding", is("UTF-8")))
                .andExpect(jsonPath("$.delimiter", is(",")));
        }

        @Test
        @DisplayName("Should return tab delimiter for a .tsv file")
        void detectFormat_TsvFile_ReturnsTabDelimiter() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                "file", "data.tsv", "text/tab-separated-values", "col\tval\n".getBytes());
            when(formatRegistry.detectFormat("data.tsv")).thenReturn(Optional.of("tsv"));
            when(formatRegistry.getFormatInfo("tsv")).thenReturn(Optional.empty());

            mockMvc.perform(multipart("/api/v1/data/detect-format").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delimiter", is("\t")));
        }

        @Test
        @DisplayName("Should fall back to 'csv' when format cannot be detected")
        void detectFormat_UnknownExtension_DefaultsToCsv() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                "file", "data.unknownext", "application/octet-stream", new byte[]{});
            when(formatRegistry.detectFormat("data.unknownext")).thenReturn(Optional.empty());

            mockMvc.perform(multipart("/api/v1/data/detect-format").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format", is("csv")));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/data/formats
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/data/formats — list available formats")
    class GetFormatsTests {

        @Test
        @DisplayName("Should return 200 with list of format info objects")
        void getFormats_Returns200WithFormatList() throws Exception {
            DataFormatInfo csvInfo = new DataFormatInfo(
                "csv", "CSV", "Comma-separated values", "text/csv",
                List.of("csv"), true, true, true, Map.of(), List.of("read", "write"));
            when(formatRegistry.getAvailableFormats()).thenReturn(List.of(csvInfo));

            mockMvc.perform(get("/api/v1/data/formats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].format", is("csv")));
        }

        @Test
        @DisplayName("Should return 200 with empty list when no format handlers are registered")
        void getFormats_NoHandlers_Returns200WithEmptyList() throws Exception {
            when(formatRegistry.getAvailableFormats()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/data/formats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/data/storage/providers/active
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/data/storage/providers/active — active storage provider")
    class GetActiveStorageProviderTests {

        @Test
        @DisplayName("Should return 200 with provider info when a provider is active")
        void getActiveStorageProvider_ActiveProvider_Returns200() throws Exception {
            StorageProviderInfo info = new StorageProviderInfo(
                "local", "Local Storage", "Filesystem-based local storage",
                "RDF Forge", null, Map.of(), List.of("upload", "download", "delete"));
            when(fileStorageService.getActiveProviderInfo()).thenReturn(info);
            when(fileStorageService.getActiveProviderType()).thenReturn("local");

            mockMvc.perform(get("/api/v1/data/storage/providers/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("local")));
        }

        @Test
        @DisplayName("Should return 404 when no storage provider is configured")
        void getActiveStorageProvider_NoProvider_Returns404() throws Exception {
            when(fileStorageService.getActiveProviderInfo()).thenReturn(null);

            mockMvc.perform(get("/api/v1/data/storage/providers/active"))
                .andExpect(status().isNotFound());
        }
    }
}
