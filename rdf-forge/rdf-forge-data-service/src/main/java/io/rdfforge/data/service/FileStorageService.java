package io.rdfforge.data.service;

import io.rdfforge.data.storage.StorageProvider;
import io.rdfforge.data.storage.StorageProvider.StorageObject;
import io.rdfforge.data.storage.StorageProviderInfo;
import io.rdfforge.data.storage.StorageProviderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * File storage service that uses the modular storage provider registry.
 *
 * This service provides a unified API for file storage operations,
 * delegating to the configured storage provider (MinIO, S3, Azure, GCS, Local).
 *
 * The active storage provider is configured via the storage.provider property.
 */
@Service
@Slf4j
public class FileStorageService {

    private final StorageProviderRegistry providerRegistry;

    public FileStorageService(StorageProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    /**
     * Get the active storage provider.
     */
    private StorageProvider getProvider() {
        StorageProvider provider = providerRegistry.getActiveProvider();
        if (provider == null) {
            throw new IllegalStateException("No storage provider configured");
        }
        return provider;
    }

    /**
     * Upload a file to storage.
     * @param file The file to upload
     * @param prefix The path prefix (directory)
     * @return The object path/key
     */
    public String uploadFile(MultipartFile file, String prefix) throws IOException {
        String objectName = prefix + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        getProvider().upload(
            file.getInputStream(),
            objectName,
            file.getContentType(),
            file.getSize()
        );

        return objectName;
    }

    /**
     * Upload a file with a specific path.
     * @param inputStream The file content
     * @param objectPath The full object path
     * @param contentType The MIME type
     * @param size The file size
     * @return The storage object info
     */
    public StorageObject uploadFile(InputStream inputStream, String objectPath, String contentType, long size) throws IOException {
        return getProvider().upload(inputStream, objectPath, contentType, size);
    }

    /**
     * Download a file from storage.
     * @param objectName The object path/key
     * @return InputStream to read the file
     */
    public InputStream downloadFile(String objectName) throws IOException {
        return getProvider().download(objectName);
    }

    /**
     * Delete a file from storage.
     * @param objectName The object path/key
     */
    public void deleteFile(String objectName) throws IOException {
        getProvider().delete(objectName);
    }

    /**
     * Check if a file exists.
     * @param objectName The object path/key
     * @return true if the file exists
     */
    public boolean fileExists(String objectName) throws IOException {
        return getProvider().exists(objectName);
    }

    /**
     * Get file info.
     * @param objectName The object path/key
     * @return Optional containing the file info if found
     */
    public Optional<StorageObject> getFileInfo(String objectName) throws IOException {
        return getProvider().getObjectInfo(objectName);
    }

    /**
     * List files in a path.
     * @param prefix The path prefix
     * @param maxKeys Maximum number of files to return
     * @return List of storage objects
     */
    public List<StorageObject> listFiles(String prefix, int maxKeys) throws IOException {
        return getProvider().list(prefix, maxKeys);
    }

    /**
     * Get a presigned download URL.
     * @param objectName The object path/key
     * @param expiryMinutes URL expiry time in minutes
     * @return The presigned URL
     */
    public String getPresignedUrl(String objectName, int expiryMinutes) throws IOException {
        return getProvider().getPresignedDownloadUrl(objectName, expiryMinutes * 60);
    }

    /**
     * Get a presigned upload URL.
     * @param objectName The object path/key
     * @param expiryMinutes URL expiry time in minutes
     * @return The presigned URL
     */
    public String getPresignedUploadUrl(String objectName, int expiryMinutes) throws IOException {
        return getProvider().getPresignedUploadUrl(objectName, expiryMinutes * 60);
    }

    /**
     * Copy a file to a new location.
     * @param sourcePath Source object path
     * @param destPath Destination object path
     * @return The copied file info
     */
    public StorageObject copyFile(String sourcePath, String destPath) throws IOException {
        return getProvider().copy(sourcePath, destPath);
    }

    /**
     * Move a file to a new location.
     * @param sourcePath Source object path
     * @param destPath Destination object path
     * @return The moved file info
     */
    public StorageObject moveFile(String sourcePath, String destPath) throws IOException {
        return getProvider().move(sourcePath, destPath);
    }

    // ==================== Provider Discovery ====================

    /**
     * Get all available storage providers.
     */
    public List<StorageProviderInfo> getAvailableProviders() {
        return providerRegistry.getAvailableProviders();
    }

    /**
     * Get the active provider type.
     */
    public String getActiveProviderType() {
        return providerRegistry.getActiveProviderType();
    }

    /**
     * Get info about the active provider.
     */
    public StorageProviderInfo getActiveProviderInfo() {
        StorageProvider provider = providerRegistry.getActiveProvider();
        return provider != null ? provider.getProviderInfo() : null;
    }
}
