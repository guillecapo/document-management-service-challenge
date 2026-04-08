# Architecture Decision Records

Decision log for the Document Management Service. Each entry documents what was decided, why, and the trade-offs considered.

---

## ADR-001 — Hexagonal Architecture (Ports and Adapters)

**Decision:** Structure the application in three layers: domain, application, and infrastructure. The domain and application layers expose interfaces (ports) that infrastructure adapters implement.

**Why:** Keeps business logic independent of frameworks, databases, and external services. Enables testing the domain and application layers in isolation without spinning up Spring, MinIO, or PostgreSQL.

**Trade-offs:**
- More files and indirection compared to a layered (MVC) approach.
- Mapping between domain models and JPA entities adds boilerplate (`toEntity` / `toDomain`).
- Justified by the testability gain and the ability to swap adapters (e.g., replace MinIO with S3) without touching business logic.

---

## ADR-002 — ULID (Monotonic) as Document ID

**Decision:** Use `ulid-creator` (`UlidCreator.getMonotonicUlid()`) to generate document IDs instead of UUID.

**Why:** ULIDs are lexicographically sortable by creation time, which improves index performance on `created_at`-ordered queries in PostgreSQL. Monotonic variant guarantees ordering within the same millisecond.

**Trade-offs:**
- Less universally known than UUID — requires the `ulid-creator` dependency.
- ULIDs are 128-bit like UUIDs but stored as 26-char strings, slightly larger than UUID's canonical 36-char representation but more compact when stored as bytes.
- Sortability benefit is meaningful at scale; for low-volume scenarios UUID would be equally valid.

---

## ADR-003 — Multipart/form-data for Upload Endpoint

**Decision:** Accept `multipart/form-data` with two parts: `metadata` (JSON) and `file` (binary), despite the OpenAPI spec only showing `application/json` with no file field.

**Why:** The spec as provided is incomplete — uploading a PDF requires transmitting binary content. `multipart/form-data` is the standard HTTP mechanism for mixed binary + structured data uploads. The alternative (Base64-encoding the file in a JSON field) would increase payload size by ~33% and is incompatible with the 50MB memory constraint.

**Trade-offs:**
- Deviates from the provided spec, which required a judgment call.
- Clients must construct a multipart request instead of a plain JSON body.
- The metadata part is a JSON object, keeping the structured data strongly typed and validatable via Bean Validation.

---

## ADR-004 — Streaming Upload to MinIO (No Buffering)

**Decision:** Pass the `InputStream` from `MultipartFile` directly to MinIO's `PutObjectArgs`, with `file-size-threshold: 0` and `location: /tmp` in the multipart configuration.

**Why:** The service runs under a 50MB JVM heap limit (`-Xmx50m`) but must handle files up to 500MB and 10 concurrent uploads. Buffering file content in memory would immediately exceed the heap. Streaming to `/tmp` first (Spring's multipart handling) and then to MinIO keeps heap usage minimal regardless of file size.

**Trade-offs:**
- Files are written to disk (`/tmp`) before being streamed to MinIO — adds latency and requires sufficient disk space on the container.
- The alternative (passing `file.getBytes()`) is simpler but would crash the JVM under the memory constraint.
- `/tmp` should be considered ephemeral — acceptable since it is only used as a transit buffer.

---

## ADR-005 — JPA Specification for Dynamic Search

**Decision:** Use `Spring Data JPA Specification` (Criteria API) to build the search query dynamically based on the provided filters.

**Why:** All three search filters (`user`, `name`, `tags`) are optional. A Specification composes predicates conditionally at runtime without string concatenation or multiple hardcoded query methods.

**Trade-offs:**
- More verbose than a JPQL `@Query`.
- Criteria API is harder to read than JPQL for complex queries.
- Justified because the number of filter combinations (2³ = 8) would require many `@Query` variants or a custom query builder otherwise.

---

## ADR-006 — Tags Stored in a Separate Table

**Decision:** Store document tags in a `document_tags` table with a foreign key to `documents`, mapped as `@OneToMany`.

**Why:** A normalized schema allows filtering documents by tag via a JOIN without parsing array columns. Avoids PostgreSQL-specific array types, keeping the schema portable.

**Trade-offs:**
- Requires a JOIN for every query that includes tags.
- More writes on upload (one INSERT per tag).
- The alternative (PostgreSQL `text[]` array column) would be simpler and perform better for reads, but couples the schema to PostgreSQL and complicates JPA mapping.

---

## ADR-007 — OR Semantics for Tag Filtering

**Decision:** When multiple tags are provided in the search filter, return documents that match **at least one** of the tags (OR logic), not all of them (AND logic).

**Why:** OR semantics are more permissive and return more results, which is the expected behavior for exploratory search. AND semantics would return an empty result set for tag combinations that no document satisfies.

**Trade-offs:**
- OR may return more results than the user expects if they intend to narrow down by multiple tags.
- AND could be offered as a future enhancement (separate filter field or query parameter).

---

## ADR-008 — Substring Match for Name Search

**Decision:** Search by `name` uses `LIKE %term%` (case-insensitive substring match) instead of exact match or full-text search.

**Why:** Substring matching is the natural expectation for a name search field — users search for partial words, not exact document names.

**Trade-offs:**
- `LIKE %term%` cannot use a B-tree index (leading wildcard prevents index scan), so it performs a full table scan at scale.
- Full-text search (PostgreSQL `tsvector` + `GIN` index) would scale better but adds significant complexity.
- For the scope of this challenge, substring match is sufficient. A `GIN` trigram index (`pg_trgm`) could be added later to support indexed `LIKE %term%` without changing the query.

---

## ADR-009 — FetchType.LAZY on Tags

**Decision:** The `@OneToMany` relationship between `DocumentJpaEntity` and `DocumentTagJpaEntity` uses `FetchType.LAZY`.

**Why:** `EAGER` on `@OneToMany` triggers a separate `SELECT` per loaded entity (N+1) regardless of whether the tags are needed. `LAZY` defers the load until the collection is accessed. Since all access happens inside `@Transactional` methods, the session is always open when tags are accessed.

**Trade-offs:**
- LAZY with N+1 and EAGER with N+1 are equivalent in query count here, since tags are always accessed. The real gain is avoiding unintended loads in future code paths where tags are not needed.
- A `@BatchSize` annotation could be added to batch-load tags across multiple documents in a single query if N+1 becomes a performance concern.

---

## ADR-010 — DependencyUnavailableException in the Domain Layer

**Decision:** Place `DependencyUnavailableException` in the domain exception package, not in infrastructure.

**Why:** The concept of "a required external dependency is unavailable" is a domain-level concern — it affects business operation regardless of which technology is down. Placing it in infrastructure would couple the domain to specific adapters.

**Trade-offs:**
- The domain exception is somewhat abstract (it does not name the failing technology).
- The technology name is captured in the log (infrastructure concern) but never surfaced to the client (security concern).

---

## ADR-011 — 503 for Dependency Failures (not 424)

**Decision:** Return `HTTP 503 Service Unavailable` when MinIO or PostgreSQL is unreachable, instead of `HTTP 424 Failed Dependency`.

**Why:** `503` means "the server cannot handle the request right now" — semantically correct for a downstream service being down. `424 Failed Dependency` is defined in WebDAV (RFC 4918) and means "this request failed because another request it depended on failed" — not applicable here.

**Trade-offs:**
- 503 is more commonly used and understood by clients and monitoring tools.
- 503 responses should ideally include a `Retry-After` header — not implemented for simplicity, but worth adding in production.

---

## ADR-012 — Technology Names Not Exposed in Error Responses

**Decision:** Error responses never include the name of the underlying technology (PostgreSQL, MinIO). They return a generic message; full details are logged.

**Why:** Exposing technology names in HTTP responses is an information disclosure vulnerability. It helps attackers identify the stack and target known CVEs.

**Trade-offs:**
- Less informative for API consumers debugging integration issues.
- Teams with access to logs get the full context. External clients only need to know the action to take (retry, fix input, contact support).

---

## ADR-013 — 50MB Constraint Applies to Heap, Not Total JVM Memory

**Decision:** The `-Xmx50m` heap constraint is preserved but `-XX:MaxMetaspaceSize=48m` was removed. The Docker container memory limit was updated from `50M` to `256M`.

**Why:** Spring Boot 3.x with Hibernate and the MinIO SDK requires approximately 120MB of Metaspace to load all classes at startup. Restricting Metaspace to 48MB caused `OutOfMemoryError: Metaspace` before the application context was even initialized. The meaningful constraint in the challenge — preventing large files from being buffered in memory — is enforced entirely by the heap limit (`-Xmx50m`). Files streamed through the application never touch Metaspace.

**Trade-offs:**
- Container total memory is now 256MB instead of 50MB. The 50MB heap limit is still enforced.
- A 500MB file upload with 50MB heap proves the streaming approach works — no file data ever lands in heap.

---

## ADR-014 — Lazy Initialization for Reduced Startup Memory

**Decision:** `spring.main.lazy-initialization=true` added to `application.yml`.

**Why:** With lazy initialization, Spring defers bean creation until the first request instead of instantiating all beans at startup. This reduces heap pressure during bootstrap, which is the most memory-intensive phase when running under a constrained heap.

**Trade-offs:**
- The first request to each endpoint is slightly slower (bean creation happens on demand).
- Startup errors in lazily initialized beans surface on first use, not at boot — acceptable for this use case since integration tests cover all beans.

---

## ADR-015 — StoragePort.upload() Returns storagePath

**Decision:** `StoragePort.upload()` was changed from `void` to `String`, returning the storage path used by the adapter.

**Why:** Both `DocumentService` and `MinioStorageAdapter` were independently constructing the same path formula (`user + "/" + name`). This duplication meant a change to the path structure required updates in two places with no compiler enforcement. The adapter is the single owner of path construction — the service receives the path as a result of the upload operation.

**Trade-offs:**
- The domain port now returns a String, slightly increasing coupling between port and caller.
- Benefit outweighs the cost: single source of truth for path logic, compiler-enforced consistency.

---

## ADR-016 — generateDownloadUrl Instead of generatePresignedUrl

**Decision:** `StoragePort.generatePresignedUrl()` was renamed to `generateDownloadUrl()`.

**Why:** "Presigned URL" is MinIO/S3-specific terminology. A domain port should be agnostic to the underlying storage provider. Any storage implementation (local filesystem, Azure Blob, GCS) would generate a download URL through a different mechanism — the port name should reflect the intent, not the implementation.

**Trade-offs:**
- Minor rename with no behavioral change.
- Makes the port truly provider-agnostic, consistent with the hexagonal architecture principle.
