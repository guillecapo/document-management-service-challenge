package com.clara.ops.challenge.document_management_service_challenge.domain.port.in;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentSearchCriteria;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.PagedResult;

public interface SearchDocumentsUseCase {

  PagedResult<Document> search(DocumentSearchCriteria criteria, int page, int size);
}
