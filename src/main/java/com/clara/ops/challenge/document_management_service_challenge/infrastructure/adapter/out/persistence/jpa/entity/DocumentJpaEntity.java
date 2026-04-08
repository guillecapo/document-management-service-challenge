package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.persistence.jpa.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "documents", schema = "document_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentJpaEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private String id;

  @Column(name = "user_id", nullable = false)
  private String user;

  @Column(nullable = false)
  private String name;

  @Column(name = "storage_path", nullable = false)
  private String storagePath;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(name = "file_type", nullable = false)
  private String fileType;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  private List<DocumentTagJpaEntity> tags;
}
