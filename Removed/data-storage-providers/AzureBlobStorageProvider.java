package io.rdfforge.data.storage.providers;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.*;
import com.azure.storage.blob.models.*;
import com.azure.storage.blob.sas.*;
import io.rdfforge.data.storage.StorageProvider;
import io.rdfforge.data.storage.StorageProviderInfo;
import io.rdfforge.data.storage.StorageProviderInfo.ConfigField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static io.rdfforge.data.storage.StorageProviderInfo.*;

/**
 * Azure Blob Storage provider.
 *
 * Provides integration with Microsoft Azure Blob Storage.
 * Supports both connection string and account key authentication.
 */
@Component
@Slf4j
public class AzureBlobStorageProvider implements StorageProvider {

    private static final StorageProviderInfo INFO = new StorageProviderInfo(
        "azure-blob",
        "Azure Blob Storage",
        "Microsoft Azure Blob Storage. Scalable cloud storage for unstructured data.",
        "Microsoft Azure",
        "https://docs.microsoft.com/en-us/azure/storage/blobs/",
        Map.of(
            "connectionString", new ConfigField("connectionString", "Connection String", "string",
                "Azure Storage connection string (preferred method)", false, true),
            "accountName", new ConfigField("accountName", "Account Name", "string",
                "Azure Storage account name (alternative to connection string)", false),
            "accountKey", new ConfigField("accountKey", "Account Key", "string",
                "Azure Storage account key (alternative to connection string)", false, true),
            "containerName", new ConfigField("containerName", "Container Name", "string",
                "Default blob container name", true),
            "endpoint", new ConfigField("endpoint", "Custom Endpoint", "string",
                "Custom blob service endpoint (optional)", false),
            "accessTier", new ConfigField("accessTier", "Access Tier", "select",
                "Default access tier for blobs", false, "Hot",
                List.of("Hot", "Cool", "Archive"))
        ),
        List.of(
            CAPABILITY_UPLOAD,
            CAPABILITY_DOWNLOAD,
            CAPABILITY_DELETE,
            CAPABILITY_LIST,
            CAPABILITY_PRESIGNED_URL,
            CAPABILITY_VERSIONING,
            CAPABILITY_ENCRYPTION,
            CAPABILITY_LIFECYCLE
        )
    );

    @Value("${azure.storage.connection-string:}")
    private String connectionString;

    @Value("${azure.storage.account-name:}")
    private String accountName;

    @Value("${azure.storage.account-key:}")
    private String accountKey;

    @Value("${azure.storage.container-name:rdf-forge}")
    private String containerName;

    @Value("${azure.storage.endpoint:}")
    private String endpoint;

    @Value("${azure.storage.access-tier:Hot}")
    private String accessTier;

    private BlobServiceClient serviceClient;
    private BlobContainerClient containerClient;
    private boolean initialized = false;

    @Override
    public StorageProviderInfo getProviderInfo() {
        return INFO;
    }

    @Override
    public boolean isEnabled() {
        return (connectionString != null && !connectionString.isEmpty()) ||
               (accountName != null && !accountName.isEmpty() &&
                accountKey != null && !accountKey.isEmpty());
    }

    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("containerName", containerName);
        config.put("endpoint", endpoint);
        config.put("accessTier", accessTier);
        config.put("accountName", accountName != null ? accountName : "");
        config.put("connectionString", connectionString != null && !connectionString.isEmpty() ? "***" : "");
        config.put("accountKey", accountKey != null && !accountKey.isEmpty() ? "***" : "");
        return config;
    }

    @Override
    public void initialize(Map<String, Object> config) throws IOException {
        try {
            this.connectionString = (String) config.getOrDefault("connectionString", connectionString);
            this.accountName = (String) config.getOrDefault("accountName", accountName);
            this.accountKey = (String) config.getOrDefault("accountKey", accountKey);
            this.containerName = (String) config.getOrDefault("containerName", containerName);
            this.endpoint = (String) config.getOrDefault("endpoint", endpoint);
            this.accessTier = (String) config.getOrDefault("accessTier", accessTier);

            initClient();
        } catch (Exception e) {
            throw new IOException("Failed to initialize Azure Blob provider: " + e.getMessage(), e);
        }
    }

    private void initClient() throws IOException {
        if (!isEnabled()) {
            return;
        }

        try {
            BlobServiceClientBuilder builder = new BlobServiceClientBuilder();

            if (connectionString != null && !connectionString.isEmpty()) {
                builder.connectionString(connectionString);
            } else if (accountName != null && !accountName.isEmpty()) {
                String effectiveEndpoint = endpoint != null && !endpoint.isEmpty()
                    ? endpoint
                    : String.format("https://%s.blob.core.windows.net", accountName);

                builder.endpoint(effectiveEndpoint)
                    .credential(new com.azure.storage.common.StorageSharedKeyCredential(accountName, accountKey));
            }

            serviceClient = builder.buildClient();
            containerClient = serviceClient.getBlobContainerClient(containerName);

            // Ensure container exists
            if (!containerClient.exists()) {
                containerClient.create();
                log.info("Created Azure blob container: {}", containerName);
            }

            initialized = true;
            log.info("Azure Blob Storage client initialized for container: {}", containerName);

        } catch (Exception e) {
            throw new IOException("Failed to initialize Azure Blob Storage client: " + e.getMessage(), e);
        }
    }

    private void ensureInitialized() throws IOException {
        if (!initialized && isEnabled()) {
            initClient();
        }
        if (containerClient == null) {
            throw new IOException("Azure Blob Storage client not initialized");
        }
    }

    @Override
    public StorageObject upload(InputStream inputStream, String objectPath, String contentType, long size) throws IOException {
        ensureInitialized();
        try {
            BlobClient blobClient = containerClient.getBlobClient(objectPath);

            BlobParallelUploadOptions options = new BlobParallelUploadOptions(inputStream, size);
            options.setHeaders(new BlobHttpHeaders().setContentType(contentType));

            AccessTier tier = AccessTier.fromString(accessTier);
            if (tier != null) {
                options.setTier(tier);
            }

            blobClient.uploadWithResponse(options, null, null);

            BlobProperties properties = blobClient.getProperties();

            return new StorageObject(
                objectPath,
                containerName,
                properties.getBlobSize(),
                properties.getContentType(),
                properties.getETag(),
                properties.getLastModified().toInstant(),
                Map.of()
            );
        } catch (Exception e) {
            throw new IOException("Failed to upload to Azure Blob: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectPath) throws IOException {
        ensureInitialized();
        try {
            BlobClient blobClient = containerClient.getBlobClient(objectPath);
            return blobClient.openInputStream();
        } catch (Exception e) {
            throw new IOException("Failed to download from Azure Blob: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectPath) throws IOException {
        ensureInitialized();
        try {
            BlobClient blobClient = containerClient.getBlobClient(objectPath);
            blobClient.delete();
        } catch (Exception e) {
            throw new IOException("Failed to delete from Azure Blob: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String objectPath) throws IOException {
        ensureInitialized();
        try {
            BlobClient blobClient = containerClient.getBlobClient(objectPath);
            return blobClient.exists();
        } catch (Exception e) {
            throw new IOException("Failed to check existence in Azure Blob: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<StorageObject> getObjectInfo(String objectPath) throws IOException {
        ensureInitialized();
        try {
            BlobClient blobClient = containerClient.getBlobClient(objectPath);

            if (!blobClient.exists()) {
                return Optional.empty();
            }

            BlobProperties properties = blobClient.getProperties();

            return Optional.of(new StorageObject(
                objectPath,
                containerName,
                properties.getBlobSize(),
                properties.getContentType(),
                properties.getETag(),
                properties.getLastModified().toInstant(),
                Map.of()
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<StorageObject> list(String prefix, int maxKeys) throws IOException {
        ensureInitialized();
        try {
            ListBlobsOptions options = new ListBlobsOptions()
                .setPrefix(prefix)
                .setMaxResultsPerPage(maxKeys);

            return containerClient.listBlobs(options, null).stream()
                .limit(maxKeys)
                .map(item -> new StorageObject(
                    item.getName(),
                    containerName,
                    item.getProperties().getContentLength(),
                    item.getProperties().getContentType(),
                    item.getProperties().getETag(),
                    item.getProperties().getLastModified().toInstant(),
                    Map.of()
                ))
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IOException("Failed to list blobs in Azure: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPresignedDownloadUrl(String objectPath, int expirySeconds) throws IOException {
        ensureInitialized();
        try {
            BlobClient blobClient = containerClient.getBlobClient(objectPath);

            OffsetDateTime expiryTime = OffsetDateTime.now().plusSeconds(expirySeconds);

            BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);

            BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, permission);

            String sasToken = blobClient.generateSas(sasValues);

            return blobClient.getBlobUrl() + "?" + sasToken;
        } catch (Exception e) {
            throw new IOException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPresignedUploadUrl(String objectPath, int expirySeconds) throws IOException {
        ensureInitialized();
        try {
            BlobClient blobClient = containerClient.getBlobClient(objectPath);

            OffsetDateTime expiryTime = OffsetDateTime.now().plusSeconds(expirySeconds);

            BlobSasPermission permission = new BlobSasPermission()
                .setWritePermission(true)
                .setCreatePermission(true);

            BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, permission);

            String sasToken = blobClient.generateSas(sasValues);

            return blobClient.getBlobUrl() + "?" + sasToken;
        } catch (Exception e) {
            throw new IOException("Failed to generate presigned upload URL: " + e.getMessage(), e);
        }
    }

    @Override
    public StorageObject copy(String sourcePath, String destPath) throws IOException {
        ensureInitialized();
        try {
            BlobClient sourceBlob = containerClient.getBlobClient(sourcePath);
            BlobClient destBlob = containerClient.getBlobClient(destPath);

            destBlob.beginCopy(sourceBlob.getBlobUrl(), null);

            // Wait for copy to complete
            int maxAttempts = 30;
            int attempt = 0;
            while (attempt < maxAttempts) {
                BlobProperties props = destBlob.getProperties();
                if (props.getCopyStatus() == CopyStatusType.SUCCESS) {
                    break;
                } else if (props.getCopyStatus() == CopyStatusType.FAILED) {
                    throw new IOException("Copy failed: " + props.getCopyStatusDescription());
                }
                Thread.sleep(100);
                attempt++;
            }

            return getObjectInfo(destPath).orElseThrow(() ->
                new IOException("Failed to get info for copied blob: " + destPath));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Copy interrupted", e);
        } catch (Exception e) {
            throw new IOException("Failed to copy blob in Azure: " + e.getMessage(), e);
        }
    }
}
