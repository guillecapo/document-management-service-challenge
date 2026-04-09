package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.persistence.jpa;

import com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.persistence.jpa.entity.DocumentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentJpaRepository
    extends JpaRepository<DocumentJpaEntity, String>, JpaSpecificationExecutor<DocumentJpaEntity> {}
