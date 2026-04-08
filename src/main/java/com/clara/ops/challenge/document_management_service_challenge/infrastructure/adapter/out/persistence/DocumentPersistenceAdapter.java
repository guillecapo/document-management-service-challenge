package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.persistence;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentSearchCriteria;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.PagedResult;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.out.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.persistence.jpa.DocumentJpaRepository;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.persistence.jpa.entity.DocumentJpaEntity;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.persistence.jpa.entity.DocumentTagJpaEntity;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DocumentPersistenceAdapter implements DocumentRepository {

  private final DocumentJpaRepository jpaRepository;

  @Override
  @Transactional
  public Document save(Document document) {
    DocumentJpaEntity entity = toEntity(document);
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Document> findById(String id) {
    return jpaRepository.findById(id).map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<Document> findByCriteria(DocumentSearchCriteria criteria, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<DocumentJpaEntity> result =
        jpaRepository.findAll(DocumentSpecification.withCriteria(criteria), pageable);

    List<Document> documents = result.getContent().stream().map(this::toDomain).toList();

    return new PagedResult<>(
        documents,
        result.getNumber(),
        result.getSize(),
        result.getTotalPages(),
        result.getTotalElements());
  }

  private DocumentJpaEntity toEntity(Document document) {
    DocumentJpaEntity entity =
        new DocumentJpaEntity(
            document.id(),
            document.user(),
            document.name(),
            document.storagePath(),
            document.fileSize(),
            document.fileType(),
            document.createdAt(),
            null);

    List<DocumentTagJpaEntity> tags =
        document.tags().stream().map(tag -> new DocumentTagJpaEntity(null, entity, tag)).toList();

    entity.setTags(tags);
    return entity;
  }

  private Document toDomain(DocumentJpaEntity entity) {
    List<String> tags = entity.getTags().stream().map(DocumentTagJpaEntity::getTag).toList();

    return new Document(
        entity.getId(),
        entity.getUser(),
        entity.getName(),
        tags,
        entity.getStoragePath(),
        entity.getFileSize(),
        entity.getFileType(),
        entity.getCreatedAt());
  }
}
