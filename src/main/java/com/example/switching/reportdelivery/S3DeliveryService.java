package com.example.switching.reportdelivery;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;

@Component
public class S3DeliveryService implements ReportDeliveryChannel {

    private final ReportDestinationResolver resolver;

    public S3DeliveryService(ReportDestinationResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.S3;
    }

    @Override
    public DeliveryResult deliver(
            ReportArtifactService.StoredArtifact artifact,
            Map<String, Object> destination) {
        String endpoint = validateEndpoint(resolver.required(destination, "endpoint"));
        String bucket = validateBucket(resolver.required(destination, "bucket"));
        String accessKey = resolver.secretReference(destination, "accessKey");
        String secretKey = resolver.secretReference(destination, "secretKey");
        String prefix = validatePrefix(String.valueOf(
                destination.getOrDefault("prefix", "reports")));
        String fileName = validateFileName(artifact.fileName());
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            if (!client.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucket)
                    .build())) {
                throw new IllegalStateException("Destination bucket does not exist");
            }
            String objectName = prefix.isEmpty()
                    ? fileName
                    : prefix + "/" + fileName;
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(
                            new ByteArrayInputStream(artifact.content()),
                            artifact.sizeBytes(),
                            -1)
                    .contentType(artifact.contentType())
                    .build());
            return new DeliveryResult("s3://" + bucket + "/" + objectName);
        } catch (Exception exception) {
            throw new IllegalStateException("S3 delivery failed", exception);
        }
    }

    private static String validateEndpoint(String raw) {
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            if (host == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                            || ("http".equalsIgnoreCase(uri.getScheme())
                                    && isLoopback(host)))) {
                throw new IllegalArgumentException(
                        "S3 endpoint must use HTTPS; HTTP is allowed only for loopback tests");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid S3 endpoint", exception);
        }
    }

    private static boolean isLoopback(String host) {
        try {
            return "localhost".equalsIgnoreCase(host)
                    || InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception exception) {
            return false;
        }
    }

    private static String validateBucket(String bucket) {
        if (!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) {
            throw new IllegalArgumentException("Invalid S3 bucket name");
        }
        return bucket;
    }

    private static String validatePrefix(String prefix) {
        String normalized = prefix == null
                ? ""
                : prefix.trim().replaceAll("^/+|/+$", "");
        if (normalized.contains("..")
                || normalized.contains("\\")
                || !normalized.matches("[A-Za-z0-9/_=-]{0,512}")) {
            throw new IllegalArgumentException("Invalid S3 object prefix");
        }
        return normalized;
    }

    private static String validateFileName(String fileName) {
        if (fileName == null
                || !fileName.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,254}")
                || fileName.contains("..")) {
            throw new IllegalArgumentException("Invalid report artifact file name");
        }
        return fileName;
    }
}
