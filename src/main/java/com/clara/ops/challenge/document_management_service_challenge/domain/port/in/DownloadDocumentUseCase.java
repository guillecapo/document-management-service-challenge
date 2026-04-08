package com.clara.ops.challenge.document_management_service_challenge.domain.port.in;

public interface DownloadDocumentUseCase {

  String generateDownloadUrl(String documentId);
}
