package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.persistence;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentSearchCriteria;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.persistence.jpa.entity.DocumentJpaEntity;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.persistence.jpa.entity.DocumentTagJpaEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class DocumentSpecification {

  private DocumentSpecification() {}

  public static Specification<DocumentJpaEntity> withCriteria(DocumentSearchCriteria criteria) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (criteria.user() != null && !criteria.user().isBlank()) {
        predicates.add(cb.equal(root.get("user"), criteria.user()));
      }

      if (criteria.name() != null && !criteria.name().isBlank()) {
        predicates.add(
            cb.like(cb.lower(root.get("name")), "%" + criteria.name().toLowerCase() + "%"));
      }

      if (criteria.tags() != null && !criteria.tags().isEmpty()) {
        Join<DocumentJpaEntity, DocumentTagJpaEntity> tagsJoin = root.join("tags", JoinType.INNER);
        predicates.add(tagsJoin.get("tag").in(criteria.tags()));
        if (!Long.class.equals(query.getResultType())) {
          query.distinct(true);
        }
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
