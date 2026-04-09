package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentSearchCriteria;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.PagedResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration tests for DocumentPersistenceAdapter against a real PostgreSQL instance managed by
 * Testcontainers. Validates save, findById, and findByCriteria (user filter, partial name match,
 * tag OR logic, ordering, and pagination). Each test runs in a transaction that is rolled back
 * automatically by @DataJpaTest, so tests are fully isolated.
 */
@DataJpaTest
@Testcontainers
@Import(DocumentPersistenceAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(
    scripts = "file:docker/init-scripts/schema-init.sql",
    executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
class DocumentPersistenceAdapterIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15.4");

  @Autowired private DocumentPersistenceAdapter adapter;

  private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 15, 10, 0);

  // ---------------------------------------------------------------------------
  // save / findById
  // ---------------------------------------------------------------------------

  /**
   * Saving a document and retrieving it by ID must return a domain object with all fields intact,
   * including the tags list persisted via the @OneToMany relationship.
   */
  @Test
  @DisplayName("save and findById returns document with all fields including tags")
  void save_and_findById_returnPersistedDocumentWithAllFields() {
    Document doc =
        buildDocument(
            "01JPMTEST00001", "alice", "contract.pdf", List.of("legal", "finance"), BASE_TIME);

    adapter.save(doc);
    Optional<Document> found = adapter.findById("01JPMTEST00001");

    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo("01JPMTEST00001");
    assertThat(found.get().user()).isEqualTo("alice");
    assertThat(found.get().name()).isEqualTo("contract.pdf");
    assertThat(found.get().tags()).containsExactlyInAnyOrder("legal", "finance");
    assertThat(found.get().fileSize()).isEqualTo(1024L);
    assertThat(found.get().fileType()).isEqualTo("application/pdf");
  }

  /** Querying a non-existent ID must return an empty Optional without throwing an exception. */
  @Test
  @DisplayName("findById with non-existent id returns empty Optional")
  void findById_nonExistentId_returnsEmpty() {
    Optional<Document> found = adapter.findById("NON_EXISTENT_ID");

    assertThat(found).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // findByCriteria — filters
  // ---------------------------------------------------------------------------

  /**
   * The user filter must match documents by exact user value and exclude documents from other
   * users.
   */
  @Test
  @DisplayName("findByCriteria with user filter returns only matching documents")
  void findByCriteria_byUser_returnsOnlyMatchingDocuments() {
    adapter.save(buildDocument("01JPMTEST00001", "alice", "doc1.pdf", List.of("legal"), BASE_TIME));
    adapter.save(
        buildDocument("01JPMTEST00002", "bob", "doc2.pdf", List.of("hr"), BASE_TIME.minusHours(1)));

    PagedResult<Document> result =
        adapter.findByCriteria(new DocumentSearchCriteria("alice", null, null), 0, 20);

    assertThat(result.totalItems()).isEqualTo(1);
    assertThat(result.items().get(0).user()).isEqualTo("alice");
  }

  /**
   * The name filter uses case-insensitive LIKE matching. A partial substring must match documents
   * whose name contains that substring regardless of case.
   */
  @Test
  @DisplayName("findByCriteria with partial name match returns matching documents")
  void findByCriteria_byPartialName_returnsMatchingDocuments() {
    adapter.save(
        buildDocument(
            "01JPMTEST00001", "alice", "annual-contract.pdf", List.of("legal"), BASE_TIME));
    adapter.save(
        buildDocument(
            "01JPMTEST00002", "alice", "invoice.pdf", List.of("finance"), BASE_TIME.minusHours(1)));

    PagedResult<Document> result =
        adapter.findByCriteria(new DocumentSearchCriteria(null, "CONTRACT", null), 0, 20);

    assertThat(result.totalItems()).isEqualTo(1);
    assertThat(result.items().get(0).name()).isEqualTo("annual-contract.pdf");
  }

  /**
   * The tags filter uses OR logic: a document matching any of the requested tags is included. A
   * document that has two of the requested tags must appear exactly once (distinct enforced).
   */
  @Test
  @DisplayName("findByCriteria with tags filter uses OR logic and deduplicates results")
  void findByCriteria_byTags_usesOrLogicAndDeduplicates() {
    // doc1 has both "legal" and "finance" — without DISTINCT it would appear twice
    adapter.save(
        buildDocument(
            "01JPMTEST00001", "alice", "doc1.pdf", List.of("legal", "finance"), BASE_TIME));
    adapter.save(
        buildDocument(
            "01JPMTEST00002", "alice", "doc2.pdf", List.of("hr"), BASE_TIME.minusHours(1)));
    adapter.save(
        buildDocument(
            "01JPMTEST00003", "alice", "doc3.pdf", List.of("ops"), BASE_TIME.minusHours(2)));

    PagedResult<Document> result =
        adapter.findByCriteria(
            new DocumentSearchCriteria(null, null, List.of("legal", "finance")), 0, 20);

    assertThat(result.totalItems()).isEqualTo(1);
    assertThat(result.items().get(0).id()).isEqualTo("01JPMTEST00001");
  }

  // ---------------------------------------------------------------------------
  // findByCriteria — ordering and pagination
  // ---------------------------------------------------------------------------

  /**
   * With no filters, results must be ordered by createdAt DESC so the most recently uploaded
   * document appears first.
   */
  @Test
  @DisplayName("findByCriteria with no filters returns all documents ordered by createdAt DESC")
  void findByCriteria_noFilters_returnsAllOrderedByCreatedAtDesc() {
    adapter.save(
        buildDocument(
            "01JPMTEST00001", "alice", "older.pdf", List.of("legal"), BASE_TIME.minusHours(2)));
    adapter.save(buildDocument("01JPMTEST00002", "bob", "newer.pdf", List.of("hr"), BASE_TIME));

    PagedResult<Document> result =
        adapter.findByCriteria(new DocumentSearchCriteria(null, null, null), 0, 20);

    assertThat(result.totalItems()).isEqualTo(2);
    assertThat(result.items().get(0).id()).isEqualTo("01JPMTEST00002");
    assertThat(result.items().get(1).id()).isEqualTo("01JPMTEST00001");
  }

  /**
   * Pagination metadata (currentPage, itemsPerPage, totalItems, totalPages) must reflect the actual
   * data and the requested page size.
   */
  @Test
  @DisplayName("findByCriteria pagination returns correct page metadata")
  void findByCriteria_pagination_returnsCorrectMetadata() {
    for (int i = 1; i <= 5; i++) {
      adapter.save(
          buildDocument(
              String.format("01JPMTEST%05d", i),
              "alice",
              "doc" + i + ".pdf",
              List.of("legal"),
              BASE_TIME.minusMinutes(i)));
    }

    PagedResult<Document> result =
        adapter.findByCriteria(new DocumentSearchCriteria(null, null, null), 0, 2);

    assertThat(result.currentPage()).isEqualTo(0);
    assertThat(result.itemsPerPage()).isEqualTo(2);
    assertThat(result.totalItems()).isEqualTo(5);
    assertThat(result.totalPages()).isEqualTo(3);
    assertThat(result.items()).hasSize(2);
  }

  // ---------------------------------------------------------------------------
  // DocumentSpecification — blank/empty field branches
  // ---------------------------------------------------------------------------

  /**
   * A blank user string satisfies the null check but fails the isBlank check in
   * DocumentSpecification, so it must be treated as no filter and return all documents.
   */
  @Test
  @DisplayName("findByCriteria with blank user treats it as no filter")
  void findByCriteria_blankUser_treatedAsNoFilter() {
    adapter.save(buildDocument("01JPMTEST00001", "alice", "doc1.pdf", List.of("legal"), BASE_TIME));
    adapter.save(
        buildDocument("01JPMTEST00002", "bob", "doc2.pdf", List.of("hr"), BASE_TIME.minusHours(1)));

    PagedResult<Document> result =
        adapter.findByCriteria(new DocumentSearchCriteria("", null, null), 0, 20);

    assertThat(result.totalItems()).isEqualTo(2);
  }

  /**
   * A blank name string satisfies the null check but fails the isBlank check in
   * DocumentSpecification, so it must be treated as no filter and return all documents.
   */
  @Test
  @DisplayName("findByCriteria with blank name treats it as no filter")
  void findByCriteria_blankName_treatedAsNoFilter() {
    adapter.save(buildDocument("01JPMTEST00001", "alice", "doc1.pdf", List.of("legal"), BASE_TIME));
    adapter.save(
        buildDocument(
            "01JPMTEST00002", "alice", "doc2.pdf", List.of("hr"), BASE_TIME.minusHours(1)));

    PagedResult<Document> result =
        adapter.findByCriteria(new DocumentSearchCriteria(null, "   ", null), 0, 20);

    assertThat(result.totalItems()).isEqualTo(2);
  }

  /**
   * An empty tags list satisfies the null check but fails the isEmpty check in
   * DocumentSpecification, so it must be treated as no filter and return all documents.
   */
  @Test
  @DisplayName("findByCriteria with empty tags list treats it as no filter")
  void findByCriteria_emptyTags_treatedAsNoFilter() {
    adapter.save(buildDocument("01JPMTEST00001", "alice", "doc1.pdf", List.of("legal"), BASE_TIME));
    adapter.save(
        buildDocument(
            "01JPMTEST00002", "alice", "doc2.pdf", List.of("hr"), BASE_TIME.minusHours(1)));

    PagedResult<Document> result =
        adapter.findByCriteria(new DocumentSearchCriteria(null, null, List.of()), 0, 20);

    assertThat(result.totalItems()).isEqualTo(2);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Document buildDocument(
      String id, String user, String name, List<String> tags, LocalDateTime createdAt) {
    return new Document(
        id, user, name, tags, user + "/" + name, 1024L, "application/pdf", createdAt);
  }
}
