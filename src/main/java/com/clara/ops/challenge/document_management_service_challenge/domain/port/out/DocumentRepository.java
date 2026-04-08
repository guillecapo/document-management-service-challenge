package com.clara.ops.challenge.document_management_service_challenge.domain.port.out;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentSearchCriteria;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.PagedResult;
import java.util.Optional;

public interface DocumentRepository {

  Document save(Document document);

  Optional<Document> findById(String id);

  PagedResult<Document> findByCriteria(DocumentSearchCriteria criteria, int page, int size);
}
