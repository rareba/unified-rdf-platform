package io.rdfforge.data.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for cloud storage providers.
 *
 * Each provider implementation must be a Spring component and will be
 * automatically discovered by the StorageProviderRegistry.
 *
 * To add a new storage provider:
 * 1. Create a class implementing this interface
 * 2. Annotate it with @Component
 * 3. Implement getProviderInfo() with provider metadata
 * 4. Implement the storage operations
 *
 * The provider will be automatically registered and available via the API.
 */
public interface StorageProvider {

    /**
     * Get metadata about this storage provider.
     * @return Provider information including name, config fields, and capabilities
     */
    StorageProviderInfo getProviderInfo();

    /**
     * Get the provider type identifier.
     * @return The type identifier (e.g., "minio", "s3", "azure", "gcs")
     */
    default String getType() {
        return getProviderInfo().type();
    }

    /**
     * Check if this provider is enabled and available.
     * @return true if the provider is configured and available
     */
    boolean isEnabled();

    /**
     * Get the current configuration for this provider.
     * @return Map of configuration values
     */
    Map<String, Object> getConfiguration();

    /**
     * Initialize the provider with configuration.
     * @param config Configuration map
     * @throws IOException if initialization fails
     */
    void initialize(Map<String, Object> config) throws IOException;

    /**
     * Upload a file to storage.
     * @param inputStream The file content
     * @param objectPath The destination path/key
     * @param contentType The MIME type of the content
     * @param size The size of the content in bytes
     * @return StorageObject with upload result
     * @throws IOException if upload fails
     */
    StorageObject upload(InputStream inputStream, String objectPath, String contentType, long size) throws IOException;

    /**
     * Download a file from storage.
     * @param objectPath The path/key of the object
     * @return InputStream to read the object
     * @throws IOException if download fails
     */
    InputStream download(String objectPath) throws IOException;

    /**
     * Delete a file from storage.
     * @param objectPath The path/key of the object
     * @throws IOException if deletion fails
     */
    void delete(String objectPath) throws IOException;

    /**
     * Check if an object exists.
     * @param objectPath The path/key of the object
     * @return true if the object exists
     * @throws IOException if check fails
     */
    boolean exists(String objectPath) throws IOException;

    /**
     * Get metadata about an object.
     * @param objectPath The path/key of the object
     * @return Optional containing the object info if found
     * @throws IOException if retrieval fails
     */
    Optional<StorageObject> getObjectInfo(String objectPath) throws IOException;

    /**
     * List objects in a path/prefix.
     * @param prefix The path prefix to list
     * @param maxKeys Maximum number of objects to return
     * @return List of storage objects
     * @throws IOException if listing fails
     */
    List<StorageObject> list(String prefix, int maxKeys) throws IOException;

    /**
     * Generate a presigned URL for downloading an object.
     * @param objectPath The path/key of the object
     * @param expirySeconds Number of seconds until URL expires
     * @return The presigned URL
     * @throws IOException if URL generation fails
     */
    String getPresignedDownloadUrl(String objectPath, int expirySeconds) throws IOException;

    /**
     * Generate a presigned URL for uploading an object.
     * @param objectPath The path/key of the object
     * @param expirySeconds Number of seconds until URL expires
     * @return The presigned URL
     * @throws IOException if URL generation fails
     */
    String getPresignedUploadUrl(String objectPath, int expirySeconds) throws IOException;

    /**
     * Copy an object to a new location.
     * @param sourcePath Source object path
     * @param destPath Destination object path
     * @return StorageObject for the copied object
     * @throws IOException if copy fails
     */
    StorageObject copy(String sourcePath, String destPath) throws IOException;

    /**
     * Move an object to a new location.
     * @param sourcePath Source object path
     * @param destPath Destination object path
     * @return StorageObject for the moved object
     * @throws IOException if move fails
     */
    default StorageObject move(String sourcePath, String destPath) throws IOException {
        StorageObject result = copy(sourcePath, destPath);
        delete(sourcePath);
        return result;
    }

    /**
     * Represents an object in storage.
     */
    record StorageObject(
        String key,
        String bucket,
        long size,
        String contentType,
        String etag,
        java.time.Instant lastModified,
        Map<String, String> metadata
    ) {}
}
