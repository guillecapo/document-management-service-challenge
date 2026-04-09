package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.in.web.dto;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.PagedResult;
import java.util.List;

public record PaginatedDocumentResponse(Metadata metadata, List<DocumentResponse> documents) {

  public record Metadata(
      int currentPage, int itemsPerPage, int currentItems, int totalPages, long totalItems) {}

  public static PaginatedDocumentResponse from(PagedResult<Document> pagedResult) {
    List<DocumentResponse> documents =
        pagedResult.items().stream().map(DocumentResponse::from).toList();

    Metadata metadata =
        new Metadata(
            pagedResult.currentPage(),
            pagedResult.itemsPerPage(),
            documents.size(),
            pagedResult.totalPages(),
            pagedResult.totalItems());

    return new PaginatedDocumentResponse(metadata, documents);
  }
}
