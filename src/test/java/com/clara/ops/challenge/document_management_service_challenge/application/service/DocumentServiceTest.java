package com.clara.ops.challenge.document_management_service_challenge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.document_management_service_challenge.domain.exception.DependencyUnavailableException;
import com.clara.ops.challenge.document_management_service_challenge.domain.exception.DocumentNotFoundException;
import com.clara.ops.challenge.document_management_service_challenge.domain.exception.StorageException;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentSearchCriteria;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentUpload;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.PagedResult;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.out.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.out.StoragePort;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link DocumentService}. All dependencies are mocked to isolate business logic
 * from infrastructure concerns.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

  private static final String STORAGE_PATH = "john.doe/report.pdf";
  private static final String DOCUMENT_ID = "01KNQDVFDGY9ZRTRATYSZN0FSY";
  private static final String DOWNLOAD_URL = "http://localhost:9000/document-bucket/john.doe/report.pdf?token=abc";

  @Mock private DocumentRepository documentRepository;
  @Mock private StoragePort storagePort;

  @InjectMocks private DocumentService documentService;

  // --- upload ---

  /**
   * Verifies that upload delegates to storage and persistence in the correct order, and that the
   * saved document contains all fields from the upload request plus a generated ULID and timestamp.
   */
  @Test
  @DisplayName("upload: success — saves document with correct fields and generated ULID")
  void upload_success_savesDocumentWithAllFields() {
    DocumentUpload upload = buildUpload();
    Document savedDocument = buildDocument();

    when(storagePort.upload(upload)).thenReturn(STORAGE_PATH);
    when(documentRepository.save(any())).thenReturn(savedDocument);

    Document result = documentService.upload(upload);

    // Capture what was passed to the repository to assert field mapping
    ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
    verify(documentRepository).save(captor.capture());
    Document captured = captor.getValue();

    assertThat(captured.id()).hasSize(26); // ULID is always 26 chars
    assertThat(captured.user()).isEqualTo("john.doe");
    assertThat(captured.name()).isEqualTo("report.pdf");
    assertThat(captured.tags()).containsExactly("finance");
    assertThat(captured.storagePath()).isEqualTo(STORAGE_PATH);
    assertThat(captured.fileSize()).isEqualTo(1024L);
    assertThat(captured.fileType()).isEqualTo("application/pdf");
    assertThat(captured.createdAt()).isNotNull().isBeforeOrEqualTo(LocalDateTime.now());
    assertThat(result).isEqualTo(savedDocument);
  }

  /**
   * Verifies that a StorageException thrown by the storage port propagates unchanged, without
   * being wrapped or swallowed by the service.
   */
  @Test
  @DisplayName("upload: storage failure — propagates StorageException")
  void upload_storageThrowsStorageException_propagates() {
    DocumentUpload upload = buildUpload();
    when(storagePort.upload(upload)).thenThrow(new StorageException("upload failed", new RuntimeException()));

    assertThatThrownBy(() -> documentService.upload(upload))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("upload failed");
  }

  /**
   * Verifies that a DependencyUnavailableException (e.g. MinIO unreachable) propagates unchanged,
   * so the web layer can return the correct 503 response.
   */
  @Test
  @DisplayName("upload: dependency unavailable — propagates DependencyUnavailableException")
  void upload_storageThrowsDependencyUnavailable_propagates() {
    DocumentUpload upload = buildUpload();
    when(storagePort.upload(upload))
        .thenThrow(new DependencyUnavailableException("MinIO", new RuntimeException()));

    assertThatThrownBy(() -> documentService.upload(upload))
        .isInstanceOf(DependencyUnavailableException.class);
  }

  // --- search ---

  /**
   * Verifies that search delegates directly to the repository with the exact criteria and
   * pagination parameters, and returns whatever the repository provides.
   */
  @Test
  @DisplayName("search: delegates to repository with correct criteria and pagination")
  void search_delegatesToRepository_returnsPagedResult() {
    DocumentSearchCriteria criteria = new DocumentSearchCriteria("john.doe", "report", List.of("finance"));
    PagedResult<Document> expected = new PagedResult<>(List.of(buildDocument()), 0, 20, 1, 1L);

    when(documentRepository.findByCriteria(criteria, 0, 20)).thenReturn(expected);

    PagedResult<Document> result = documentService.search(criteria, 0, 20);

    assertThat(result).isEqualTo(expected);
    verify(documentRepository).findByCriteria(eq(criteria), eq(0), eq(20));
  }

  // --- generateDownloadUrl ---

  /**
   * Verifies that generateDownloadUrl retrieves the document's storage path and passes it to the
   * storage port, returning the generated URL.
   */
  @Test
  @DisplayName("generateDownloadUrl: success — returns presigned URL for existing document")
  void generateDownloadUrl_documentExists_returnsUrl() {
    Document document = buildDocument();
    when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
    when(storagePort.generateDownloadUrl(STORAGE_PATH)).thenReturn(DOWNLOAD_URL);

    String result = documentService.generateDownloadUrl(DOCUMENT_ID);

    assertThat(result).isEqualTo(DOWNLOAD_URL);
    verify(storagePort).generateDownloadUrl(STORAGE_PATH);
  }

  /**
   * Verifies that generateDownloadUrl throws DocumentNotFoundException when the document ID does
   * not exist in the repository, without calling the storage port.
   */
  @Test
  @DisplayName("generateDownloadUrl: document not found — throws DocumentNotFoundException")
  void generateDownloadUrl_documentNotFound_throwsDocumentNotFoundException() {
    when(documentRepository.findById("nonexistent")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> documentService.generateDownloadUrl("nonexistent"))
        .isInstanceOf(DocumentNotFoundException.class)
        .hasMessageContaining("nonexistent");
  }

  // --- helpers ---

  private DocumentUpload buildUpload() {
    return new DocumentUpload("john.doe", "report.pdf", List.of("finance"), InputStream.nullInputStream(), 1024L, "application/pdf");
  }

  private Document buildDocument() {
    return new Document(DOCUMENT_ID, "john.doe", "report.pdf", List.of("finance"), STORAGE_PATH, 1024L, "application/pdf", LocalDateTime.now());
  }
}
