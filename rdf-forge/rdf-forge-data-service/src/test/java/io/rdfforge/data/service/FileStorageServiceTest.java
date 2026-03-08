package io.rdfforge.data.service;

import io.rdfforge.data.storage.StorageProvider;
import io.rdfforge.data.storage.StorageProvider.StorageObject;
import io.rdfforge.data.storage.StorageProviderInfo;
import io.rdfforge.data.storage.StorageProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for FileStorageService.
 * Tests file storage operations, path sanitization, and security.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileStorageService Tests")
class FileStorageServiceTest {

    @Mock
    private StorageProviderRegistry providerRegistry;

    @Mock
    private StorageProvider storageProvider;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        when(providerRegistry.getActiveProvider()).thenReturn(storageProvider);
        fileStorageService = new FileStorageService(providerRegistry);
    }

    @Nested
    @DisplayName("uploadFile(MultipartFile, String) Tests")
    class UploadMultipartFileTests {

        @Test
        @DisplayName("Should upload file successfully")
        void uploadFile_ValidFile_Succeeds() throws IOException {
            MultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "content".getBytes()
            );
            when(storageProvider.upload(any(), anyString(), anyString(), anyLong()))
                .thenReturn(createStorageObject("data-sources/uuid-test.csv"));

            String result = fileStorageService.uploadFile(file, "data-sources");

            assertNotNull(result);
            assertTrue(result.startsWith("data-sources/"));
            assertTrue(result.endsWith("-test.csv"));
            verify(storageProvider).upload(any(), contains("data-sources/"), eq("text/csv"), eq(7L));
        }

        @Test
        @DisplayName("Should sanitize filename with path traversal attempt")
        void uploadFile_PathTraversalInFilename_Sanitizes() throws IOException {
            MultipartFile file = new MockMultipartFile(
                "file", "../../../etc/passwd", "text/plain", "content".getBytes()
            );
            when(storageProvider.upload(any(), anyString(), anyString(), anyLong()))
                .thenReturn(createStorageObject("uploads/uuid-etc_passwd"));

            String result = fileStorageService.uploadFile(file, "uploads");

            assertNotNull(result);
            assertFalse(result.contains("../"));
            assertFalse(result.contains("/etc/"));
            assertTrue(result.contains("etc_passwd"));
        }

        @Test
        @DisplayName("Should handle null filename")
        void uploadFile_NullFilename_UsesUnnamed() throws IOException {
            MultipartFile file = new MockMultipartFile(
                "file", null, "application/octet-stream", "content".getBytes()
            );
            when(storageProvider.upload(any(), anyString(), anyString(), anyLong()))
                .thenReturn(createStorageObject("uploads/uuid-unnamed"));

            String result = fileStorageService.uploadFile(file, "uploads");

            assertNotNull(result);
            assertTrue(result.contains("unnamed"));
        }

        @Test
        @DisplayName("Should sanitize path prefix with path traversal")
        void uploadFile_PathTraversalInPrefix_Sanitizes() throws IOException {
            MultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv", "content".getBytes()
            );
            when(storageProvider.upload(any(), anyString(), anyString(), anyLong()))
                .thenReturn(createStorageObject("uploads/uuid-data.csv"));

            String result = fileStorageService.uploadFile(file, "../etc");

            assertNotNull(result);
            assertFalse(result.contains("../"));
        }

        @Test
        @DisplayName("Should limit long filenames")
        void uploadFile_LongFilename_Truncates() throws IOException {
            String longName = "a".repeat(300) + ".csv";
            MultipartFile file = new MockMultipartFile(
                "file", longName, "text/csv", "content".getBytes()
            );
            when(storageProvider.upload(any(), anyString(), anyString(), anyLong()))
                .thenReturn(createStorageObject("uploads/uuid-truncated"));

            String result = fileStorageService.uploadFile(file, "uploads");

            assertNotNull(result);
            // Path should not exceed reasonable limits
            assertTrue(result.length() < 400);
        }

        @Test
        @DisplayName("Should preserve file extension when truncating")
        void uploadFile_LongNameWithExtension_PreservesExtension() throws IOException {
            String longName = "a".repeat(300) + ".csv";
            MultipartFile file = new MockMultipartFile(
                "file", longName, "text/csv", "content".getBytes()
            );
            when(storageProvider.upload(any(), anyString(), anyString(), anyLong()))
                .thenReturn(createStorageObject("uploads/uuid-truncated.csv"));

            String result = fileStorageService.uploadFile(file, "uploads");

            assertNotNull(result);
            // Extension should be preserved
            assertTrue(result.endsWith(".csv") || result.endsWith("_csv"));
        }

        @Test
        @DisplayName("Should remove control characters from filename")
        void uploadFile_FilenameWithControlChars_RemovesThem() throws IOException {
            MultipartFile file = new MockMultipartFile(
                "file", "test\x00\x01file.csv", "text/csv", "content".getBytes()
            );
            when(storageProvider.upload(any(), anyString(), anyString(), anyLong()))
                .thenReturn(createStorageObject("uploads/uuid-test_file.csv"));

            String result = fileStorageService.uploadFile(file, "uploads");

            assertNotNull(result);
            verify(storageProvider).upload(any(), not(contains("\x00")), anyString(), anyLong());
        }

        @Test
        @DisplayName("Should sanitize dangerous characters")
        void uploadFile_FilenameWithDangerousChars_Sanitizes() throws IOException {
            MultipartFile file = new MockMultipartFile(
                "file", "test<file>.csv", "text/csv", "content".getBytes()
            );
            when(storageProvider.upload(any(), anyString(), anyString(), anyLong()))
                .thenReturn(createStorageObject("uploads/uuid-test_file_.csv"));

            String result = fileStorageService.uploadFile(file, "uploads");

            assertNotNull(result);
            // Dangerous characters should be replaced
            verify(storageProvider).upload(any(), not(contains("<")), anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("uploadFile(InputStream, String, String, long) Tests")
    class UploadInputStreamTests {

        @Test
        @DisplayName("Should upload from input stream")
        void uploadFile_InputStream_Succeeds() throws IOException {
            InputStream inputStream = new ByteArrayInputStream("content".getBytes());
            StorageObject expectedObject = createStorageObject("custom/path/file.txt");
            when(storageProvider.upload(any(), eq("custom/path/file.txt"), eq("text/plain"), eq(100L)))
                .thenReturn(expectedObject);

            StorageObject result = fileStorageService.uploadFile(
                inputStream, "custom/path/file.txt", "text/plain", 100L
            );

            assertNotNull(result);
            assertEquals("custom/path/file.txt", result.getPath());
        }
    }

    @Nested
    @DisplayName("downloadFile Tests")
    class DownloadFileTests {

        @Test
        @DisplayName("Should download file successfully")
        void downloadFile_ValidPath_ReturnsStream() throws IOException {
            InputStream expectedStream = new ByteArrayInputStream("content".getBytes());
            when(storageProvider.download("data/file.csv")).thenReturn(expectedStream);

            InputStream result = fileStorageService.downloadFile("data/file.csv");

            assertNotNull(result);
            verify(storageProvider).download("data/file.csv");
        }

        @Test
        @DisplayName("Should propagate IOException on download failure")
        void downloadFile_Failure_ThrowsIOException() throws IOException {
            when(storageProvider.download("data/file.csv"))
                .thenThrow(new IOException("File not found"));

            assertThrows(IOException.class, () ->
                fileStorageService.downloadFile("data/file.csv")
            );
        }
    }

    @Nested
    @DisplayName("deleteFile Tests")
    class DeleteFileTests {

        @Test
        @DisplayName("Should delete file successfully")
        void deleteFile_ValidPath_Succeeds() throws IOException {
            doNothing().when(storageProvider).delete("data/file.csv");

            assertDoesNotThrow(() -> fileStorageService.deleteFile("data/file.csv"));
            verify(storageProvider).delete("data/file.csv");
        }

        @Test
        @DisplayName("Should propagate IOException on delete failure")
        void deleteFile_Failure_ThrowsIOException() throws IOException {
            doThrow(new IOException("Permission denied"))
                .when(storageProvider).delete("data/file.csv");

            assertThrows(IOException.class, () ->
                fileStorageService.deleteFile("data/file.csv")
            );
        }
    }

    @Nested
    @DisplayName("fileExists Tests")
    class FileExistsTests {

        @Test
        @DisplayName("Should return true when file exists")
        void fileExists_WhenExists_ReturnsTrue() throws IOException {
            when(storageProvider.exists("data/file.csv")).thenReturn(true);

            boolean result = fileStorageService.fileExists("data/file.csv");

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when file does not exist")
        void fileExists_WhenNotExists_ReturnsFalse() throws IOException {
            when(storageProvider.exists("data/file.csv")).thenReturn(false);

            boolean result = fileStorageService.fileExists("data/file.csv");

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("getFileInfo Tests")
    class GetFileInfoTests {

        @Test
        @DisplayName("Should return file info when file exists")
        void getFileInfo_WhenExists_ReturnsInfo() throws IOException {
            StorageObject expectedObject = createStorageObject("data/file.csv");
            when(storageProvider.getObjectInfo("data/file.csv"))
                .thenReturn(Optional.of(expectedObject));

            Optional<StorageObject> result = fileStorageService.getFileInfo("data/file.csv");

            assertTrue(result.isPresent());
            assertEquals("data/file.csv", result.get().getPath());
        }

        @Test
        @DisplayName("Should return empty when file does not exist")
        void getFileInfo_WhenNotExists_ReturnsEmpty() throws IOException {
            when(storageProvider.getObjectInfo("data/file.csv"))
                .thenReturn(Optional.empty());

            Optional<StorageObject> result = fileStorageService.getFileInfo("data/file.csv");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("listFiles Tests")
    class ListFilesTests {

        @Test
        @DisplayName("Should list files with prefix")
        void listFiles_WithPrefix_ReturnsList() throws IOException {
            List<StorageObject> expectedList = List.of(
                createStorageObject("data/file1.csv"),
                createStorageObject("data/file2.csv")
            );
            when(storageProvider.list("data/", 100)).thenReturn(expectedList);

            List<StorageObject> result = fileStorageService.listFiles("data/", 100);

            assertEquals(2, result.size());
        }
    }

    @Nested
    @DisplayName("getPresignedUrl Tests")
    class GetPresignedUrlTests {

        @Test
        @DisplayName("Should get presigned download URL")
        void getPresignedUrl_Download_ReturnsUrl() throws IOException {
            String expectedUrl = "https://storage.example.com/download?token=abc123";
            when(storageProvider.getPresignedDownloadUrl("data/file.csv", 3600))
                .thenReturn(expectedUrl);

            String result = fileStorageService.getPresignedUrl("data/file.csv", 60);

            assertEquals(expectedUrl, result);
            // Verify expiry is converted from minutes to seconds
            verify(storageProvider).getPresignedDownloadUrl("data/file.csv", 3600);
        }

        @Test
        @DisplayName("Should get presigned upload URL")
        void getPresignedUrl_Upload_ReturnsUrl() throws IOException {
            String expectedUrl = "https://storage.example.com/upload?token=xyz789";
            when(storageProvider.getPresignedUploadUrl("data/file.csv", 1800))
                .thenReturn(expectedUrl);

            String result = fileStorageService.getPresignedUploadUrl("data/file.csv", 30);

            assertEquals(expectedUrl, result);
            // Verify expiry is converted from minutes to seconds
            verify(storageProvider).getPresignedUploadUrl("data/file.csv", 1800);
        }
    }

    @Nested
    @DisplayName("copyFile Tests")
    class CopyFileTests {

        @Test
        @DisplayName("Should copy file successfully")
        void copyFile_ValidPaths_Succeeds() throws IOException {
            StorageObject expectedObject = createStorageObject("dest/file.csv");
            when(storageProvider.copy("source/file.csv", "dest/file.csv"))
                .thenReturn(expectedObject);

            StorageObject result = fileStorageService.copyFile("source/file.csv", "dest/file.csv");

            assertNotNull(result);
            assertEquals("dest/file.csv", result.getPath());
        }
    }

    @Nested
    @DisplayName("moveFile Tests")
    class MoveFileTests {

        @Test
        @DisplayName("Should move file successfully")
        void moveFile_ValidPaths_Succeeds() throws IOException {
            StorageObject expectedObject = createStorageObject("dest/file.csv");
            when(storageProvider.move("source/file.csv", "dest/file.csv"))
                .thenReturn(expectedObject);

            StorageObject result = fileStorageService.moveFile("source/file.csv", "dest/file.csv");

            assertNotNull(result);
            assertEquals("dest/file.csv", result.getPath());
        }
    }

    @Nested
    @DisplayName("Provider Discovery Tests")
    class ProviderDiscoveryTests {

        @Test
        @DisplayName("Should get available providers")
        void getAvailableProviders_ReturnsList() {
            List<StorageProviderInfo> expectedList = List.of(
                StorageProviderInfo.builder()
                    .type("minio")
                    .name("MinIO")
                    .available(true)
                    .build()
            );
            when(providerRegistry.getAvailableProviders()).thenReturn(expectedList);

            List<StorageProviderInfo> result = fileStorageService.getAvailableProviders();

            assertEquals(1, result.size());
            assertEquals("minio", result.get(0).getType());
        }

        @Test
        @DisplayName("Should get active provider type")
        void getActiveProviderType_ReturnsType() {
            when(providerRegistry.getActiveProviderType()).thenReturn("minio");

            String result = fileStorageService.getActiveProviderType();

            assertEquals("minio", result);
        }

        @Test
        @DisplayName("Should get active provider info")
        void getActiveProviderInfo_ReturnsInfo() {
            StorageProviderInfo expectedInfo = StorageProviderInfo.builder()
                .type("minio")
                .name("MinIO")
                .available(true)
                .build();
            when(storageProvider.getProviderInfo()).thenReturn(expectedInfo);

            StorageProviderInfo result = fileStorageService.getActiveProviderInfo();

            assertNotNull(result);
            assertEquals("minio", result.getType());
        }

        @Test
        @DisplayName("Should return null when no active provider")
        void getActiveProviderInfo_NoProvider_ReturnsNull() {
            when(providerRegistry.getActiveProvider()).thenReturn(null);
            // Recreate service with null provider
            FileStorageService service = new FileStorageService(providerRegistry);

            StorageProviderInfo result = service.getActiveProviderInfo();

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should throw IllegalStateException when no provider configured")
        void uploadFile_NoProvider_ThrowsException() {
            when(providerRegistry.getActiveProvider()).thenReturn(null);
            FileStorageService service = new FileStorageService(providerRegistry);

            MultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "content".getBytes()
            );

            assertThrows(IllegalStateException.class, () ->
                service.uploadFile(file, "uploads")
            );
        }

        @Test
        @DisplayName("Should handle empty prefix")
        void uploadFile_EmptyPrefix_UsesDefault() throws IOException {
            MultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "content".getBytes()
            );
            when(storageProvider.upload(any(), anyString(), anyString(), anyLong()))
                .thenReturn(createStorageObject("uploads/uuid-test.csv"));

            fileStorageService.uploadFile(file, "");

            verify(storageProvider).upload(any(), contains("uploads"), anyString(), anyLong());
        }

        @Test
        @DisplayName("Should handle null prefix")
        void uploadFile_NullPrefix_UsesDefault() throws IOException {
            MultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "content".getBytes()
            );
            when(storageProvider.upload(any(), anyString(), anyString(), anyLong()))
                .thenReturn(createStorageObject("uploads/uuid-test.csv"));

            fileStorageService.uploadFile(file, null);

            verify(storageProvider).upload(any(), contains("uploads"), anyString(), anyLong());
        }

        @Test
        @DisplayName("Should handle double slashes in path")
        void uploadFile_DoubleSlashes_Normalizes() throws IOException {
            MultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "content".getBytes()
            );
            when(storageProvider.upload(any(), anyString(), anyString(), anyLong()))
                .thenReturn(createStorageObject("data/test.csv"));

            fileStorageService.uploadFile(file, "data//sub//folder");

            // Path should be normalized (no double slashes)
            verify(storageProvider).upload(any(), not(contains("//")), anyString(), anyLong());
        }
    }

    private StorageObject createStorageObject(String path) {
        return StorageObject.builder()
            .path(path)
            .size(100L)
            .contentType("text/plain")
            .lastModified(Instant.now())
            .build();
    }
}
