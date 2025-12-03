package io.rdfforge.data.storage.providers;

import io.minio.*;
import io.minio.messages.Item;
import io.rdfforge.data.storage.StorageProvider;
import io.rdfforge.data.storage.StorageProviderInfo;
import io.rdfforge.data.storage.StorageProviderInfo.ConfigField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

import static io.rdfforge.data.storage.StorageProviderInfo.*;

/**
 * MinIO/S3-compatible storage provider.
 *
 * This provider works with:
 * - MinIO (self-hosted object storage)
 * - Any S3-compatible storage (AWS S3, DigitalOcean Spaces, Backblaze B2, etc.)
 */
@Component
@Slf4j
public class MinioStorageProvider implements StorageProvider {

    private static final StorageProviderInfo INFO = new StorageProviderInfo(
        "minio",
        "MinIO / S3-Compatible",
        "S3-compatible object storage. Works with MinIO, AWS S3, DigitalOcean Spaces, and more.",
        "MinIO, Inc.",
        "https://min.io/docs/minio/linux/index.html",
        Map.of(
            "endpoint", new ConfigField("endpoint", "Endpoint URL", "string",
                "The MinIO/S3 endpoint URL (e.g., http://localhost:9000)", true),
            "accessKey", new ConfigField("accessKey", "Access Key", "string",
                "Access key ID for authentication", true, true),
            "secretKey", new ConfigField("secretKey", "Secret Key", "string",
                "Secret access key for authentication", true, true),
            "bucket", new ConfigField("bucket", "Bucket Name", "string",
                "Default bucket to use for storage", true),
            "region", new ConfigField("region", "Region", "string",
                "AWS region (optional for MinIO)", false, "us-east-1"),
            "secure", new ConfigField("secure", "Use HTTPS", "boolean",
                "Use secure (HTTPS) connection", false, false)
        ),
        List.of(
            CAPABILITY_UPLOAD,
            CAPABILITY_DOWNLOAD,
            CAPABILITY_DELETE,
            CAPABILITY_LIST,
            CAPABILITY_PRESIGNED_URL,
            CAPABILITY_MULTIPART_UPLOAD,
            CAPABILITY_VERSIONING
        )
    );

    @Value("${minio.endpoint:}")
    private String endpoint;

    @Value("${minio.access-key:}")
    private String accessKey;

    @Value("${minio.secret-key:}")
    private String secretKey;

    @Value("${minio.bucket-name:rdf-forge}")
    private String bucketName;

    @Value("${minio.region:us-east-1}")
    private String region;

    @Value("${minio.secure:false}")
    private boolean secure;

    private MinioClient client;
    private boolean initialized = false;

    @Override
    public StorageProviderInfo getProviderInfo() {
        return INFO;
    }

    @Override
    public boolean isEnabled() {
        return endpoint != null && !endpoint.isEmpty();
    }

    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("endpoint", endpoint);
        config.put("bucket", bucketName);
        config.put("region", region);
        config.put("secure", secure);
        // Don't expose sensitive keys
        config.put("accessKey", accessKey != null && !accessKey.isEmpty() ? "***" : "");
        config.put("secretKey", secretKey != null && !secretKey.isEmpty() ? "***" : "");
        return config;
    }

    @Override
    public void initialize(Map<String, Object> config) throws IOException {
        try {
            this.endpoint = (String) config.getOrDefault("endpoint", endpoint);
            this.accessKey = (String) config.getOrDefault("accessKey", accessKey);
            this.secretKey = (String) config.getOrDefault("secretKey", secretKey);
            this.bucketName = (String) config.getOrDefault("bucket", bucketName);
            this.region = (String) config.getOrDefault("region", region);
            this.secure = Boolean.parseBoolean(String.valueOf(config.getOrDefault("secure", secure)));

            initClient();
        } catch (Exception e) {
            throw new IOException("Failed to initialize MinIO provider: " + e.getMessage(), e);
        }
    }

    private void initClient() throws IOException {
        if (endpoint == null || endpoint.isEmpty()) {
            return;
        }

        try {
            MinioClient.Builder builder = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey);

            if (region != null && !region.isEmpty()) {
                builder.region(region);
            }

            client = builder.build();

            // Ensure bucket exists
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created bucket: {}", bucketName);
            }

            initialized = true;
            log.info("MinIO client initialized for endpoint: {}", endpoint);

        } catch (Exception e) {
            throw new IOException("Failed to initialize MinIO client: " + e.getMessage(), e);
        }
    }

    private void ensureInitialized() throws IOException {
        if (!initialized && isEnabled()) {
            initClient();
        }
        if (client == null) {
            throw new IOException("MinIO client not initialized");
        }
    }

    @Override
    public StorageObject upload(InputStream inputStream, String objectPath, String contentType, long size) throws IOException {
        ensureInitialized();
        try {
            ObjectWriteResponse response = client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build()
            );

            return new StorageObject(
                objectPath,
                bucketName,
                size,
                contentType,
                response.etag(),
                Instant.now(),
                Map.of()
            );
        } catch (Exception e) {
            throw new IOException("Failed to upload to MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectPath) throws IOException {
        ensureInitialized();
        try {
            return client.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build()
            );
        } catch (Exception e) {
            throw new IOException("Failed to download from MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectPath) throws IOException {
        ensureInitialized();
        try {
            client.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build()
            );
        } catch (Exception e) {
            throw new IOException("Failed to delete from MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String objectPath) throws IOException {
        ensureInitialized();
        try {
            client.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Optional<StorageObject> getObjectInfo(String objectPath) throws IOException {
        ensureInitialized();
        try {
            StatObjectResponse stat = client.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build()
            );

            return Optional.of(new StorageObject(
                objectPath,
                bucketName,
                stat.size(),
                stat.contentType(),
                stat.etag(),
                stat.lastModified().toInstant(),
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
            List<StorageObject> objects = new ArrayList<>();
            Iterable<Result<Item>> results = client.listObjects(
                ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .maxKeys(maxKeys)
                    .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                objects.add(new StorageObject(
                    item.objectName(),
                    bucketName,
                    item.size(),
                    null,
                    item.etag(),
                    item.lastModified().toInstant(),
                    Map.of()
                ));
            }

            return objects;
        } catch (Exception e) {
            throw new IOException("Failed to list objects in MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPresignedDownloadUrl(String objectPath, int expirySeconds) throws IOException {
        ensureInitialized();
        try {
            return client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .method(io.minio.http.Method.GET)
                    .expiry(expirySeconds)
                    .build()
            );
        } catch (Exception e) {
            throw new IOException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPresignedUploadUrl(String objectPath, int expirySeconds) throws IOException {
        ensureInitialized();
        try {
            return client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .method(io.minio.http.Method.PUT)
                    .expiry(expirySeconds)
                    .build()
            );
        } catch (Exception e) {
            throw new IOException("Failed to generate presigned upload URL: " + e.getMessage(), e);
        }
    }

    @Override
    public StorageObject copy(String sourcePath, String destPath) throws IOException {
        ensureInitialized();
        try {
            ObjectWriteResponse response = client.copyObject(
                CopyObjectArgs.builder()
                    .bucket(bucketName)
                    .object(destPath)
                    .source(CopySource.builder()
                        .bucket(bucketName)
                        .object(sourcePath)
                        .build())
                    .build()
            );

            return getObjectInfo(destPath).orElse(new StorageObject(
                destPath, bucketName, 0, null, response.etag(), Instant.now(), Map.of()
            ));
        } catch (Exception e) {
            throw new IOException("Failed to copy object in MinIO: " + e.getMessage(), e);
        }
    }
}
