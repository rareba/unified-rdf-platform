package io.rdfforge.data.storage.providers;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import com.google.cloud.storage.Storage.BlobListOption;
import io.rdfforge.data.storage.StorageProvider;
import io.rdfforge.data.storage.StorageProviderInfo;
import io.rdfforge.data.storage.StorageProviderInfo.ConfigField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.rdfforge.data.storage.StorageProviderInfo.*;

/**
 * Google Cloud Storage (GCS) provider.
 *
 * Provides integration with Google Cloud Storage.
 * Supports authentication via service account JSON key file or
 * application default credentials.
 */
@Component
@Slf4j
public class GcsStorageProvider implements StorageProvider {

    private static final StorageProviderInfo INFO = new StorageProviderInfo(
        "gcs",
        "Google Cloud Storage",
        "Google Cloud Storage. Unified object storage with global edge caching.",
        "Google Cloud",
        "https://cloud.google.com/storage/docs",
        Map.of(
            "projectId", new ConfigField("projectId", "Project ID", "string",
                "Google Cloud project ID", true),
            "credentialsPath", new ConfigField("credentialsPath", "Credentials Path", "string",
                "Path to service account JSON key file (leave empty for default credentials)", false),
            "bucketName", new ConfigField("bucketName", "Bucket Name", "string",
                "GCS bucket name", true),
            "location", new ConfigField("location", "Location", "select",
                "Bucket location (for new buckets)", false, "US",
                List.of("US", "EU", "ASIA", "US-EAST1", "US-WEST1", "EUROPE-WEST1", "ASIA-EAST1")),
            "storageClass", new ConfigField("storageClass", "Storage Class", "select",
                "Default storage class for objects", false, "STANDARD",
                List.of("STANDARD", "NEARLINE", "COLDLINE", "ARCHIVE"))
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

    @Value("${gcs.project-id:}")
    private String projectId;

    @Value("${gcs.credentials-path:}")
    private String credentialsPath;

    @Value("${gcs.bucket-name:}")
    private String bucketName;

    @Value("${gcs.location:US}")
    private String location;

    @Value("${gcs.storage-class:STANDARD}")
    private String storageClass;

    private Storage storage;
    private boolean initialized = false;

    @Override
    public StorageProviderInfo getProviderInfo() {
        return INFO;
    }

    @Override
    public boolean isEnabled() {
        return projectId != null && !projectId.isEmpty() &&
               bucketName != null && !bucketName.isEmpty();
    }

    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("projectId", projectId);
        config.put("bucketName", bucketName);
        config.put("location", location);
        config.put("storageClass", storageClass);
        config.put("credentialsPath", credentialsPath != null && !credentialsPath.isEmpty() ? "***" : "");
        return config;
    }

    @Override
    public void initialize(Map<String, Object> config) throws IOException {
        try {
            this.projectId = (String) config.getOrDefault("projectId", projectId);
            this.credentialsPath = (String) config.getOrDefault("credentialsPath", credentialsPath);
            this.bucketName = (String) config.getOrDefault("bucketName", bucketName);
            this.location = (String) config.getOrDefault("location", location);
            this.storageClass = (String) config.getOrDefault("storageClass", storageClass);

            initClient();
        } catch (Exception e) {
            throw new IOException("Failed to initialize GCS provider: " + e.getMessage(), e);
        }
    }

    private void initClient() throws IOException {
        if (!isEnabled()) {
            return;
        }

        try {
            StorageOptions.Builder builder = StorageOptions.newBuilder()
                .setProjectId(projectId);

            if (credentialsPath != null && !credentialsPath.isEmpty()) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                    Files.newInputStream(Paths.get(credentialsPath))
                );
                builder.setCredentials(credentials);
            }
            // Otherwise, use application default credentials

            storage = builder.build().getService();

            // Ensure bucket exists
            Bucket bucket = storage.get(bucketName);
            if (bucket == null) {
                log.info("Creating GCS bucket: {}", bucketName);
                storage.create(BucketInfo.newBuilder(bucketName)
                    .setLocation(location)
                    .setStorageClass(StorageClass.valueOf(storageClass))
                    .build());
            }

            initialized = true;
            log.info("GCS client initialized for project: {}, bucket: {}", projectId, bucketName);

        } catch (Exception e) {
            throw new IOException("Failed to initialize GCS client: " + e.getMessage(), e);
        }
    }

    private void ensureInitialized() throws IOException {
        if (!initialized && isEnabled()) {
            initClient();
        }
        if (storage == null) {
            throw new IOException("GCS client not initialized");
        }
    }

    @Override
    public StorageObject upload(InputStream inputStream, String objectPath, String contentType, long size) throws IOException {
        ensureInitialized();
        try {
            BlobId blobId = BlobId.of(bucketName, objectPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .setStorageClass(StorageClass.valueOf(storageClass))
                .build();

            Blob blob = storage.createFrom(blobInfo, inputStream);

            return new StorageObject(
                objectPath,
                bucketName,
                blob.getSize(),
                blob.getContentType(),
                blob.getEtag(),
                Instant.ofEpochMilli(blob.getUpdateTimeOffsetDateTime().toInstant().toEpochMilli()),
                Map.of()
            );
        } catch (Exception e) {
            throw new IOException("Failed to upload to GCS: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectPath) throws IOException {
        ensureInitialized();
        try {
            Blob blob = storage.get(BlobId.of(bucketName, objectPath));
            if (blob == null) {
                throw new FileNotFoundException("Object not found: " + objectPath);
            }
            return Channels.newInputStream(blob.reader());
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to download from GCS: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectPath) throws IOException {
        ensureInitialized();
        try {
            storage.delete(BlobId.of(bucketName, objectPath));
        } catch (Exception e) {
            throw new IOException("Failed to delete from GCS: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String objectPath) throws IOException {
        ensureInitialized();
        try {
            Blob blob = storage.get(BlobId.of(bucketName, objectPath));
            return blob != null && blob.exists();
        } catch (Exception e) {
            throw new IOException("Failed to check existence in GCS: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<StorageObject> getObjectInfo(String objectPath) throws IOException {
        ensureInitialized();
        try {
            Blob blob = storage.get(BlobId.of(bucketName, objectPath));
            if (blob == null || !blob.exists()) {
                return Optional.empty();
            }

            return Optional.of(new StorageObject(
                objectPath,
                bucketName,
                blob.getSize(),
                blob.getContentType(),
                blob.getEtag(),
                Instant.ofEpochMilli(blob.getUpdateTimeOffsetDateTime().toInstant().toEpochMilli()),
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
            Page<Blob> blobs = storage.list(bucketName,
                BlobListOption.prefix(prefix),
                BlobListOption.pageSize(maxKeys));

            return StreamSupport.stream(blobs.getValues().spliterator(), false)
                .limit(maxKeys)
                .map(blob -> new StorageObject(
                    blob.getName(),
                    bucketName,
                    blob.getSize(),
                    blob.getContentType(),
                    blob.getEtag(),
                    Instant.ofEpochMilli(blob.getUpdateTimeOffsetDateTime().toInstant().toEpochMilli()),
                    Map.of()
                ))
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IOException("Failed to list objects in GCS: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPresignedDownloadUrl(String objectPath, int expirySeconds) throws IOException {
        ensureInitialized();
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, objectPath)).build();

            URL signedUrl = storage.signUrl(blobInfo, expirySeconds, TimeUnit.SECONDS,
                Storage.SignUrlOption.withV4Signature());

            return signedUrl.toString();
        } catch (Exception e) {
            throw new IOException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPresignedUploadUrl(String objectPath, int expirySeconds) throws IOException {
        ensureInitialized();
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, objectPath)).build();

            URL signedUrl = storage.signUrl(blobInfo, expirySeconds, TimeUnit.SECONDS,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature());

            return signedUrl.toString();
        } catch (Exception e) {
            throw new IOException("Failed to generate presigned upload URL: " + e.getMessage(), e);
        }
    }

    @Override
    public StorageObject copy(String sourcePath, String destPath) throws IOException {
        ensureInitialized();
        try {
            CopyWriter copyWriter = storage.copy(Storage.CopyRequest.newBuilder()
                .setSource(BlobId.of(bucketName, sourcePath))
                .setTarget(BlobId.of(bucketName, destPath))
                .build());

            Blob copiedBlob = copyWriter.getResult();

            return new StorageObject(
                destPath,
                bucketName,
                copiedBlob.getSize(),
                copiedBlob.getContentType(),
                copiedBlob.getEtag(),
                Instant.ofEpochMilli(copiedBlob.getUpdateTimeOffsetDateTime().toInstant().toEpochMilli()),
                Map.of()
            );
        } catch (Exception e) {
            throw new IOException("Failed to copy object in GCS: " + e.getMessage(), e);
        }
    }
}
