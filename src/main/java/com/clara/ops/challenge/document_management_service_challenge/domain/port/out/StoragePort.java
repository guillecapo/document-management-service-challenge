package com.clara.ops.challenge.document_management_service_challenge.domain.port.out;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentUploadCommand;

public interface StoragePort {

  String upload(DocumentUploadCommand command);

  String generatePresignedUrl(String storagePath);
}
