package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.in.web.dto;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.Document;
import java.util.List;

public record DocumentResponse(
    String id,
    String user,
    String name,
    List<String> tags,
    Long size,
    String type,
    String createdAt) {

  public static DocumentResponse from(Document document) {
    return new DocumentResponse(
        document.id(),
        document.user(),
        document.name(),
        document.tags(),
        document.fileSize(),
        document.fileType(),
        document.createdAt().toString());
  }
}
