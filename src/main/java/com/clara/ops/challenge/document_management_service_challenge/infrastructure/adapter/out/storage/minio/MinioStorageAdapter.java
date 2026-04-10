package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.storage.minio;

import com.clara.ops.challenge.document_management_service_challenge.domain.exception.DependencyUnavailableException;
import com.clara.ops.challenge.document_management_service_challenge.domain.exception.StorageException;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentUpload;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.out.StoragePort;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.config.MinioProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.net.ConnectException;
import java.net.SocketException;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class MinioStorageAdapter implements StoragePort {

  private final MinioClient minioClient;
  private final MinioClient presignedMinioClient;
  private final MinioProperties properties;

  public MinioStorageAdapter(
      MinioClient minioClient,
      @Qualifier("presignedMinioClient") MinioClient presignedMinioClient,
      MinioProperties properties) {
    this.minioClient = minioClient;
    this.presignedMinioClient = presignedMinioClient;
    this.properties = properties;
  }

  @Override
  public String upload(DocumentUpload upload) {
    // Strip any path components to prevent path traversal attacks.
    String filename = Paths.get(upload.name()).getFileName().toString();
    String storagePath = upload.user() + "/" + filename;
    try {
      minioClient.putObject(
          PutObjectArgs.builder().bucket(properties.bucketName()).object(storagePath).stream(
                  upload.fileStream(), upload.fileSize(), -1L)
              .contentType(upload.fileType())
              .build());
      return storagePath;
    } catch (Exception e) {
      throw buildStorageException("Failed to upload document to storage: " + storagePath, e);
    }
  }

  @Override
  public String generateDownloadUrl(String storagePath) {
    try {
      // Explicit region bypasses the SDK's region-detection HTTP call, allowing
      // presignedMinioClient (configured with the public endpoint) to generate
      // the URL without needing network access to the public endpoint from inside
      // the container.
      return presignedMinioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(properties.bucketName())
              .object(storagePath)
              .region(properties.region())
              .expiry(properties.presignedUrlExpiryMinutes(), TimeUnit.MINUTES)
              .build());
    } catch (Exception e) {
      throw buildStorageException("Failed to generate download URL for: " + storagePath, e);
    }
  }

  private RuntimeException buildStorageException(String message, Exception e) {
    Throwable cause = e.getCause() != null ? e.getCause() : e;
    if (cause instanceof ConnectException || cause instanceof SocketException) {
      return new DependencyUnavailableException("MinIO", e);
    }
    return new StorageException(message, e);
  }
}
