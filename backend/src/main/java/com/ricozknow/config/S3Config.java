package com.ricozknow.config;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Value("${storage.s3.endpoint}")
    private String endpoint;

    @Value("${storage.s3.region}")
    private String region;

    @Value("${storage.s3.access-key}")
    private String accessKey;

    @Value("${storage.s3.secret-key}")
    private String secretKey;

    @Value("${storage.s3.bucket}")
    private String bucket;

    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        accessKey,
                                        secretKey
                                )
                        )
                )
                // Required for MinIO and most self-hosted S3-compatible stores
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        accessKey,
                                        secretKey
                                )
                        )
                )
                .serviceConfiguration(
                        software.amazon.awssdk.services.s3.S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .build();
    }

    /**
     * Ensures the configured storage bucket exists before the application is marked
     * ready. A 403 is NOT equivalent to a missing bucket; silently continuing would
     * break uploads, imports, and other object-storage features later.
     */
    @Bean
    public ApplicationRunner initializeBucket(S3Client s3Client) {
        return args -> {
            try {
                s3Client.headBucket(HeadBucketRequest.builder()
                        .bucket(bucket)
                        .build());
                log.info("S3 bucket accessible: {}", bucket);
            } catch (S3Exception e) {
                if (e.statusCode() == 404) {
                    // Only a definite 'not found' should trigger creation.
                    try {
                        s3Client.createBucket(CreateBucketRequest.builder()
                                .bucket(bucket)
                                .build());
                        log.info("Created S3 bucket: {}", bucket);
                    } catch (S3Exception createError) {
                        throw storageError("create", createError);
                    }
                    return;
                }
                throw storageError("check", e);
            } catch (software.amazon.awssdk.core.exception.SdkClientException e) {
                throw new IllegalStateException(
                        "Cannot connect to object storage at " + endpoint
                                + ". Check S3_ENDPOINT, container DNS, and MinIO readiness.", e);
            }
        };
    }

    private IllegalStateException storageError(String operation, S3Exception error) {
        if (error.statusCode() == 403) {
            return new IllegalStateException(
                    "S3 access denied (HTTP 403) while trying to " + operation
                            + " bucket '" + bucket + "' at " + endpoint
                            + ". Check the running MinIO server, credentials, bucket policy,"
                            + " and AIStor licensing if using the AIStor image."
                            + " A 403 must not be treated as a missing bucket.", error);
        }
        return new IllegalStateException(
                "S3 " + operation + " failed for bucket '" + bucket + "' at " + endpoint
                        + " (HTTP " + error.statusCode() + ").", error);
    }
}
