package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.storage.minio;

import com.clara.ops.challenge.document_management_service_challenge.domain.exception.DependencyUnavailableException;
import com.clara.ops.challenge.document_management_service_challenge.domain.exception.StorageException;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentUpload;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.out.StoragePort;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.config.MinioProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import java.net.ConnectException;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MinioStorageAdapter implements StoragePort {

  private final MinioClient minioClient;
  private final MinioProperties properties;

  @Override
  public void upload(DocumentUpload upload) {
    String storagePath = upload.user() + "/" + upload.name();
    try {
      minioClient.putObject(
          PutObjectArgs.builder()
              .bucket(properties.bucketName())
              .object(storagePath)
              .stream(upload.fileStream(), upload.fileSize(), -1)
              .contentType(upload.fileType())
              .build());
    } catch (Exception e) {
      throwStorageException("Failed to upload document to storage: " + storagePath, e);
    }
  }

  @Override
  public String generatePresignedUrl(String storagePath) {
    try {
      return minioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(properties.bucketName())
              .object(storagePath)
              .expiry(properties.presignedUrlExpiryMinutes(), TimeUnit.MINUTES)
              .build());
    } catch (Exception e) {
      throwStorageException("Failed to generate presigned URL for: " + storagePath, e);
      return null; // unreachable, but required by the compiler
    }
  }

  private void throwStorageException(String message, Exception e) {
    Throwable cause = e.getCause() != null ? e.getCause() : e;
    if (cause instanceof ConnectException || cause instanceof SocketException) {
      throw new DependencyUnavailableException("MinIO", e);
    }
    throw new StorageException(message, e);
  }
}
