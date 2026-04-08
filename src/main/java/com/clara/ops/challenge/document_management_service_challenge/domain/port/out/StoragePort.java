package com.clara.ops.challenge.document_management_service_challenge.domain.port.out;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentUpload;

public interface StoragePort {

  String upload(DocumentUpload upload);

  String generateDownloadUrl(String storagePath);
}
