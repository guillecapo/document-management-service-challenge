package com.clara.ops.challenge.document_management_service_challenge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
    String endpoint,
    String publicEndpoint,
    String region,
    String accessKey,
    String secretKey,
    String bucketName,
    int presignedUrlExpiryMinutes) {}
