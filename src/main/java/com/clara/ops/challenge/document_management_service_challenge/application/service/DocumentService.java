package com.clara.ops.challenge.document_management_service_challenge.application.service;

import com.clara.ops.challenge.document_management_service_challenge.domain.exception.DocumentNotFoundException;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentSearchCriteria;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentUpload;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.PagedResult;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.in.DownloadDocumentUseCase;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.in.SearchDocumentsUseCase;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.in.UploadDocumentUseCase;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.out.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.out.StoragePort;
import com.github.f4b6a3.ulid.UlidCreator;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentService
    implements UploadDocumentUseCase, SearchDocumentsUseCase, DownloadDocumentUseCase {

  private final DocumentRepository documentRepository;
  private final StoragePort storagePort;

  @Override
  public Document upload(DocumentUpload upload) {
    String id = UlidCreator.getMonotonicUlid().toString();
    String storagePath = storagePort.upload(upload);

    Document document =
        new Document(
            id,
            upload.user(),
            upload.name(),
            upload.tags(),
            storagePath,
            upload.fileSize(),
            upload.fileType(),
            LocalDateTime.now());

    return documentRepository.save(document);
  }

  @Override
  public PagedResult<Document> search(DocumentSearchCriteria criteria, int page, int size) {
    return documentRepository.findByCriteria(criteria, page, size);
  }

  @Override
  public String generateDownloadUrl(String documentId) {
    Document document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));

    return storagePort.generateDownloadUrl(document.storagePath());
  }
}
