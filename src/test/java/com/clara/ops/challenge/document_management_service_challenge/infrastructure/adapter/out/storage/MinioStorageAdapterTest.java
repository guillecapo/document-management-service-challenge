package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.document_management_service_challenge.domain.exception.DependencyUnavailableException;
import com.clara.ops.challenge.document_management_service_challenge.domain.exception.StorageException;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentUpload;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.storage.minio.MinioStorageAdapter;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.config.MinioProperties;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies MinioStorageAdapter's two responsibilities: delegating upload and presigned URL
 * generation to the MinioClient, and translating connectivity failures into
 * DependencyUnavailableException while wrapping other failures as StorageException.
 */
@ExtendWith(MockitoExtension.class)
class MinioStorageAdapterTest {

  @Mock private MinioClient minioClient;
  @Mock private MinioClient presignedMinioClient;

  private MinioStorageAdapter adapter;

  @BeforeEach
  void setUp() {
    MinioProperties properties =
        new MinioProperties(
            "http://minio:9000",
            "http://localhost:9000",
            "us-east-1",
            "key",
            "secret",
            "documents",
            60);
    adapter = new MinioStorageAdapter(minioClient, presignedMinioClient, properties);
  }

  // ---------------------------------------------------------------------------
  // upload
  // ---------------------------------------------------------------------------

  /**
   * A successful putObject call must return the storage path composed as "{user}/{filename}". This
   * path is persisted in the database and used later to generate presigned download URLs.
   */
  @Test
  @DisplayName("upload success returns storage path as user/filename")
  void upload_success_returnsStoragePath() throws Exception {
    DocumentUpload upload =
        new DocumentUpload(
            "alice",
            "contract.pdf",
            List.of("legal"),
            new ByteArrayInputStream(new byte[0]),
            0L,
            "application/pdf");

    when(minioClient.putObject(any())).thenReturn(null);

    String storagePath = adapter.upload(upload);

    assertThat(storagePath).isEqualTo("alice/contract.pdf");
  }

  /**
   * ConnectException during upload signals that MinIO is unreachable. The adapter must translate
   * this into DependencyUnavailableException so the web layer returns 503 instead of 500.
   */
  @Test
  @DisplayName("upload with ConnectException throws DependencyUnavailableException")
  void upload_connectException_throwsDependencyUnavailableException() throws Exception {
    DocumentUpload upload =
        new DocumentUpload(
            "alice",
            "contract.pdf",
            List.of(),
            new ByteArrayInputStream(new byte[0]),
            0L,
            "application/pdf");

    // ConnectException extends IOException, which is declared in putObject's throws clause.
    // The adapter uses e itself as the cause when e.getCause() is null, so instanceof check works.
    doAnswer(
            inv -> {
              throw new ConnectException("refused");
            })
        .when(minioClient)
        .putObject(any());

    assertThatThrownBy(() -> adapter.upload(upload))
        .isInstanceOf(DependencyUnavailableException.class);
  }

  /**
   * SocketException during upload also signals a connectivity failure. It is treated identically to
   * ConnectException because both indicate the MinIO server is unreachable at the TCP level.
   */
  @Test
  @DisplayName("upload with SocketException throws DependencyUnavailableException")
  void upload_socketException_throwsDependencyUnavailableException() throws Exception {
    DocumentUpload upload =
        new DocumentUpload(
            "alice",
            "contract.pdf",
            List.of(),
            new ByteArrayInputStream(new byte[0]),
            0L,
            "application/pdf");

    doAnswer(
            inv -> {
              throw new SocketException("reset");
            })
        .when(minioClient)
        .putObject(any());

    assertThatThrownBy(() -> adapter.upload(upload))
        .isInstanceOf(DependencyUnavailableException.class);
  }

  /**
   * When the SDK wraps a ConnectException inside an IOException (e.g. OkHttp wrapping a TCP-level
   * failure), {@code e.getCause()} is non-null and is a ConnectException. The adapter must inspect
   * the cause and still return DependencyUnavailableException, not StorageException.
   */
  @Test
  @DisplayName(
      "upload with IOException wrapping ConnectException throws DependencyUnavailableException")
  void upload_iOExceptionWrappingConnectException_throwsDependencyUnavailableException()
      throws Exception {
    DocumentUpload upload =
        new DocumentUpload(
            "alice",
            "contract.pdf",
            List.of(),
            new ByteArrayInputStream(new byte[0]),
            0L,
            "application/pdf");

    doAnswer(
            inv -> {
              throw new IOException("connection failed", new ConnectException("refused"));
            })
        .when(minioClient)
        .putObject(any());

    assertThatThrownBy(() -> adapter.upload(upload))
        .isInstanceOf(DependencyUnavailableException.class);
  }

  /**
   * A non-connectivity IOException during upload (e.g. malformed response from MinIO) is wrapped as
   * StorageException, which maps to 500 rather than 503.
   */
  @Test
  @DisplayName("upload with generic IOException throws StorageException")
  void upload_genericException_throwsStorageException() throws Exception {
    DocumentUpload upload =
        new DocumentUpload(
            "alice",
            "contract.pdf",
            List.of(),
            new ByteArrayInputStream(new byte[0]),
            0L,
            "application/pdf");

    doAnswer(
            inv -> {
              throw new IOException("unexpected minio error");
            })
        .when(minioClient)
        .putObject(any());

    assertThatThrownBy(() -> adapter.upload(upload)).isInstanceOf(StorageException.class);
  }

  // ---------------------------------------------------------------------------
  // generateDownloadUrl
  // ---------------------------------------------------------------------------

  /**
   * A successful getPresignedObjectUrl call must return the URL as-is. The presignedMinioClient is
   * configured with the public endpoint, so the URL it generates is already externally accessible.
   */
  @Test
  @DisplayName("generateDownloadUrl success returns presigned URL unchanged")
  void generateDownloadUrl_success_returnsPresignedUrl() throws Exception {
    String expectedUrl = "http://localhost:9000/documents/alice/contract.pdf?X-Amz-Signature=abc";

    when(presignedMinioClient.getPresignedObjectUrl(any())).thenReturn(expectedUrl);

    String url = adapter.generateDownloadUrl("alice/contract.pdf");

    assertThat(url).isEqualTo(expectedUrl);
  }

  /**
   * ConnectException during URL generation signals that MinIO is unreachable. The adapter must
   * throw DependencyUnavailableException so the web layer returns 503.
   */
  @Test
  @DisplayName("generateDownloadUrl with ConnectException throws DependencyUnavailableException")
  void generateDownloadUrl_connectException_throwsDependencyUnavailableException()
      throws Exception {
    doAnswer(
            inv -> {
              throw new ConnectException("refused");
            })
        .when(presignedMinioClient)
        .getPresignedObjectUrl(any());

    assertThatThrownBy(() -> adapter.generateDownloadUrl("alice/contract.pdf"))
        .isInstanceOf(DependencyUnavailableException.class);
  }

  /**
   * A non-connectivity IOException during URL generation is wrapped as StorageException, which maps
   * to 500 rather than 503.
   */
  @Test
  @DisplayName("generateDownloadUrl with generic IOException throws StorageException")
  void generateDownloadUrl_genericException_throwsStorageException() throws Exception {
    doAnswer(
            inv -> {
              throw new IOException("minio error");
            })
        .when(presignedMinioClient)
        .getPresignedObjectUrl(any());

    assertThatThrownBy(() -> adapter.generateDownloadUrl("alice/contract.pdf"))
        .isInstanceOf(StorageException.class);
  }
}
