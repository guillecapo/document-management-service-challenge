package com.clara.ops.challenge.document_management_service_challenge.domain.exception;

public class DocumentNotFoundException extends RuntimeException {

  public DocumentNotFoundException(String documentId) {
    super("Document not found with id: " + documentId);
  }
}
