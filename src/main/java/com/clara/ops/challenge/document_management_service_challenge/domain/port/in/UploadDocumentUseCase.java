package com.clara.ops.challenge.document_management_service_challenge.domain.port.in;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentUploadCommand;

public interface UploadDocumentUseCase {

  Document upload(DocumentUploadCommand command);
}
