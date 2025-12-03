package io.rdfforge.data.storage.providers;

import io.rdfforge.data.storage.StorageProvider;
import io.rdfforge.data.storage.StorageProviderInfo;
import io.rdfforge.data.storage.StorageProviderInfo.ConfigField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static io.rdfforge.data.storage.StorageProviderInfo.*;

/**
 * AWS S3 native storage provider.
 *
 * Uses the AWS SDK v2 directly for Amazon S3.
 * Supports all AWS S3 features including:
 * - Standard S3 buckets
 * - S3 Intelligent-Tiering
 * - S3 Glacier
 * - Server-side encryption
 * - Versioning
 */
@Component
@Slf4j
public class AwsS3StorageProvider implements StorageProvider {

    private static final StorageProviderInfo INFO = new StorageProviderInfo(
        "aws-s3",
        "Amazon S3",
        "Amazon Simple Storage Service (S3). Enterprise-grade object storage with high durability.",
        "Amazon Web Services",
        "https://docs.aws.amazon.com/s3/",
        Map.of(
            "accessKeyId", new ConfigField("accessKeyId", "Access Key ID", "string",
                "AWS access key ID", true, true),
            "secretAccessKey", new ConfigField("secretAccessKey", "Secret Access Key", "string",
                "AWS secret access key", true, true),
            "region", new ConfigField("region", "AWS Region", "select",
                "AWS region for the S3 bucket", true, "us-east-1",
                List.of("us-east-1", "us-east-2", "us-west-1", "us-west-2",
                    "eu-west-1", "eu-west-2", "eu-west-3", "eu-central-1",
                    "ap-northeast-1", "ap-southeast-1", "ap-southeast-2")),
            "bucket", new ConfigField("bucket", "Bucket Name", "string",
                "S3 bucket name", true),
            "endpoint", new ConfigField("endpoint", "Custom Endpoint", "string",
                "Custom endpoint URL (optional, for S3-compatible services)", false),
            "storageClass", new ConfigField("storageClass", "Storage Class", "select",
                "S3 storage class for new objects", false, "STANDARD",
                List.of("STANDARD", "INTELLIGENT_TIERING", "STANDARD_IA", "ONEZONE_IA", "GLACIER"))
        ),
        List.of(
            CAPABILITY_UPLOAD,
            CAPABILITY_DOWNLOAD,
            CAPABILITY_DELETE,
            CAPABILITY_LIST,
            CAPABILITY_PRESIGNED_URL,
            CAPABILITY_MULTIPART_UPLOAD,
            CAPABILITY_VERSIONING,
            CAPABILITY_ENCRYPTION,
            CAPABILITY_LIFECYCLE
        )
    );

    @Value("${aws.s3.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.s3.secret-access-key:}")
    private String secretAccessKey;

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @Value("${aws.s3.bucket:}")
    private String bucketName;

    @Value("${aws.s3.endpoint:}")
    private String endpoint;

    @Value("${aws.s3.storage-class:STANDARD}")
    private String storageClass;

    private S3Client client;
    private S3Presigner presigner;
    private boolean initialized = false;

    @Override
    public StorageProviderInfo getProviderInfo() {
        return INFO;
    }

    @Override
    public boolean isEnabled() {
        return accessKeyId != null && !accessKeyId.isEmpty() &&
               secretAccessKey != null && !secretAccessKey.isEmpty() &&
               bucketName != null && !bucketName.isEmpty();
    }

    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("region", region);
        config.put("bucket", bucketName);
        config.put("endpoint", endpoint);
        config.put("storageClass", storageClass);
        config.put("accessKeyId", accessKeyId != null && !accessKeyId.isEmpty() ? "***" : "");
        config.put("secretAccessKey", secretAccessKey != null && !secretAccessKey.isEmpty() ? "***" : "");
        return config;
    }

    @Override
    public void initialize(Map<String, Object> config) throws IOException {
        try {
            this.accessKeyId = (String) config.getOrDefault("accessKeyId", accessKeyId);
            this.secretAccessKey = (String) config.getOrDefault("secretAccessKey", secretAccessKey);
            this.region = (String) config.getOrDefault("region", region);
            this.bucketName = (String) config.getOrDefault("bucket", bucketName);
            this.endpoint = (String) config.getOrDefault("endpoint", endpoint);
            this.storageClass = (String) config.getOrDefault("storageClass", storageClass);

            initClient();
        } catch (Exception e) {
            throw new IOException("Failed to initialize AWS S3 provider: " + e.getMessage(), e);
        }
    }

    private void initClient() throws IOException {
        if (!isEnabled()) {
            return;
        }

        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

            var clientBuilder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider);

            var presignerBuilder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider);

            if (endpoint != null && !endpoint.isEmpty()) {
                clientBuilder.endpointOverride(URI.create(endpoint));
                presignerBuilder.endpointOverride(URI.create(endpoint));
            }

            client = clientBuilder.build();
            presigner = presignerBuilder.build();

            // Verify bucket exists
            try {
                client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            } catch (NoSuchBucketException e) {
                log.warn("Bucket {} does not exist. Creating...", bucketName);
                client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            }

            initialized = true;
            log.info("AWS S3 client initialized for region: {}, bucket: {}", region, bucketName);

        } catch (Exception e) {
            throw new IOException("Failed to initialize AWS S3 client: " + e.getMessage(), e);
        }
    }

    private void ensureInitialized() throws IOException {
        if (!initialized && isEnabled()) {
            initClient();
        }
        if (client == null) {
            throw new IOException("AWS S3 client not initialized");
        }
    }

    @Override
    public StorageObject upload(InputStream inputStream, String objectPath, String contentType, long size) throws IOException {
        ensureInitialized();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectPath)
                .contentType(contentType)
                .storageClass(StorageClass.fromValue(storageClass))
                .build();

            PutObjectResponse response = client.putObject(request, RequestBody.fromInputStream(inputStream, size));

            return new StorageObject(
                objectPath,
                bucketName,
                size,
                contentType,
                response.eTag(),
                Instant.now(),
                Map.of()
            );
        } catch (Exception e) {
            throw new IOException("Failed to upload to AWS S3: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectPath) throws IOException {
        ensureInitialized();
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectPath)
                .build();

            return client.getObject(request);
        } catch (Exception e) {
            throw new IOException("Failed to download from AWS S3: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectPath) throws IOException {
        ensureInitialized();
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objectPath)
                .build();

            client.deleteObject(request);
        } catch (Exception e) {
            throw new IOException("Failed to delete from AWS S3: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String objectPath) throws IOException {
        ensureInitialized();
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectPath)
                .build();

            client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            throw new IOException("Failed to check existence in AWS S3: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<StorageObject> getObjectInfo(String objectPath) throws IOException {
        ensureInitialized();
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectPath)
                .build();

            HeadObjectResponse response = client.headObject(request);

            return Optional.of(new StorageObject(
                objectPath,
                bucketName,
                response.contentLength(),
                response.contentType(),
                response.eTag(),
                response.lastModified(),
                Map.of()
            ));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new IOException("Failed to get object info from AWS S3: " + e.getMessage(), e);
        }
    }

    @Override
    public List<StorageObject> list(String prefix, int maxKeys) throws IOException {
        ensureInitialized();
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .maxKeys(maxKeys)
                .build();

            ListObjectsV2Response response = client.listObjectsV2(request);

            return response.contents().stream()
                .map(obj -> new StorageObject(
                    obj.key(),
                    bucketName,
                    obj.size(),
                    null,
                    obj.eTag(),
                    obj.lastModified(),
                    Map.of()
                ))
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IOException("Failed to list objects in AWS S3: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPresignedDownloadUrl(String objectPath, int expirySeconds) throws IOException {
        ensureInitialized();
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectPath)
                .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .getObjectRequest(getObjectRequest)
                .build();

            return presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            throw new IOException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPresignedUploadUrl(String objectPath, int expirySeconds) throws IOException {
        ensureInitialized();
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectPath)
                .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .putObjectRequest(putObjectRequest)
                .build();

            return presigner.presignPutObject(presignRequest).url().toString();
        } catch (Exception e) {
            throw new IOException("Failed to generate presigned upload URL: " + e.getMessage(), e);
        }
    }

    @Override
    public StorageObject copy(String sourcePath, String destPath) throws IOException {
        ensureInitialized();
        try {
            CopyObjectRequest request = CopyObjectRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(sourcePath)
                .destinationBucket(bucketName)
                .destinationKey(destPath)
                .build();

            client.copyObject(request);

            return getObjectInfo(destPath).orElseThrow(() ->
                new IOException("Failed to get info for copied object: " + destPath));
        } catch (Exception e) {
            throw new IOException("Failed to copy object in AWS S3: " + e.getMessage(), e);
        }
    }
}
