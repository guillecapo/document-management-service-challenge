# Solution — Document Management API

This document describes the implementation decisions, setup instructions, and testing approach for the Document Management Service challenge.

---

## 1. Stack and Architecture

|   Layer    |                       Technology                       |
|------------|--------------------------------------------------------|
| Runtime    | Java 17, Spring Boot 4.0.5                             |
| Storage    | MinIO (S3-compatible) via MinIO SDK 8.6.0              |
| Database   | PostgreSQL 15.4 + Spring Data JPA                      |
| IDs        | ULID (ulid-creator 5.2.3) — sortable by creation time  |
| Testing    | JUnit 5, Mockito, AssertJ, Testcontainers              |
| Coverage   | JaCoCo 0.8.11 — 95% line and branch threshold enforced |
| Code style | Spotless (Google Java Format)                          |

The service follows **Hexagonal Architecture (Ports and Adapters)**:

```
domain/          — models, exceptions, port interfaces (no framework dependency)
application/     — use cases (DocumentService)
infrastructure/  — HTTP adapters (in), JPA and MinIO adapters (out)
```

Business logic has zero dependency on Spring, JPA, or MinIO. Adapters can be swapped without touching the domain or application layers.

---

## 2. How to Run the Stack

### Requirements

- Docker and Docker Compose (no local Java required)

### Steps

```bash
# 1. Copy the environment file
cp docker/.env.example docker/.env

# 2. Build and start all services
docker-compose -f docker/docker-compose.yml up --build
```

This starts three containers:
- `document-management-service` on port `8080`
- `postgres` on port `5432`
- `minio` on ports `9000` (API) and `9001` (console)

The database schema is initialized automatically via `docker/init-scripts/schema-init.sql`.

### Stopping the stack

```bash
docker-compose -f docker/docker-compose.yml down
```

---

## 3. Endpoints

Once the stack is running, the full interactive API documentation is available at:

**Swagger UI:** `http://localhost:8080/swagger-ui.html`

| Method |                     Path                     |                      Description                      |
|--------|----------------------------------------------|-------------------------------------------------------|
| `POST` | `/document-management/upload`                | Upload a PDF with metadata (multipart/form-data)      |
| `POST` | `/document-management/search`                | Search documents with optional filters and pagination |
| `GET`  | `/document-management/download/{documentId}` | Get a temporary download URL for a document           |

A Postman collection with pre-configured requests is available at `scripts/document-management.postman_collection.json`. Set the `baseUrl` variable to `http://localhost:8080`.

---

## 4. Running Tests

```bash
# Run all tests and enforce the 95% coverage threshold
./mvnw verify
```

The test suite includes:

|                 Test class                  |             Type             |                  What it covers                   |
|---------------------------------------------|------------------------------|---------------------------------------------------|
| `DocumentServiceTest`                       | Unit                         | Application layer use cases                       |
| `GlobalExceptionHandlerTest`                | Unit                         | HTTP error mappings (400, 404, 413, 415, 503)     |
| `MinioStorageAdapterTest`                   | Unit                         | MinIO adapter paths and error handling            |
| `DocumentControllerTest`                    | Unit (MockMvc)               | Controller request/response mapping               |
| `DocumentPersistenceAdapterIntegrationTest` | Integration (Testcontainers) | JPA queries against a real PostgreSQL instance    |
| `ConcurrentUploadIntegrationTest`           | Integration (Testcontainers) | 10 concurrent uploads of 60 MB each under -Xmx50m |

#### Coverage report

After running `./mvnw verify`, open the report at:

```
target/site/jacoco/index.html
```

---

## 5. The 50 MB Memory Constraint

The challenge requires the service to handle uploads of up to 500 MB under a 50 MB JVM heap limit.

**How it works:**

1. Spring's multipart configuration writes incoming file bytes to `/tmp` (disk), never to heap.
2. The `InputStream` from the temp file is passed directly to MinIO's `PutObjectArgs` — no in-memory buffering.
3. `spring.main.lazy-initialization=true` reduces heap pressure at startup.
4. `-Xmx50m` is enforced; the Docker container limit is `256M` to allow Metaspace (~120 MB required by Spring Boot 4.x class loading).

**Validated by** `ConcurrentUploadIntegrationTest`: 10 threads uploading 60 MB files simultaneously against real MinIO and PostgreSQL containers, with heap metrics logged per thread. All uploads complete successfully without `OutOfMemoryError`.

See [ADR-004](#) and [ADR-013](#) in `docs/decisions.md` for the full reasoning.

---

## 6. Architecture Decisions

All decisions made during implementation are documented as Architecture Decision Records (ADRs) in:

**[docs/decisions.md](docs/decisions.md)**

The file contains 16 ADRs covering every non-trivial choice made. Key highlights:

|   ADR   |                    Decision                     |                                          Why it matters                                           |
|---------|-------------------------------------------------|---------------------------------------------------------------------------------------------------|
| ADR-003 | `multipart/form-data` for upload                | The provided spec showed no file field — binary uploads require multipart, not JSON               |
| ADR-004 | Streaming to MinIO via `InputStream`            | Only way to stay under the 50 MB heap limit with 500 MB files                                     |
| ADR-013 | `-Xmx50m` heap only, no `MaxMetaspaceSize`      | Spring Boot 4.x needs ~120 MB Metaspace; the constraint targets heap buffering, not class loading |
| ADR-012 | Technology names not exposed in error responses | Exposing PostgreSQL/MinIO names in HTTP responses is an information disclosure vulnerability      |

---

## 7. Notes for the Reviewer

- **Upload contract deviation**: the provided OpenAPI spec defines `/upload` with `content: application/json` and only the metadata fields (user, name, tags) — there is no file field. A binary PDF cannot be transmitted inside a JSON body. The implementation uses `multipart/form-data` with two parts: `metadata` (JSON, same schema as the spec) and `file` (binary). The response contract (201 / 400 / 422 / 503) is identical to the spec. See ADR-003 in `docs/decisions.md` for the full reasoning.
- **Memory constraint**: the 50 MB limit applies to JVM heap (`-Xmx50m`). Metaspace is intentionally unconstrained — see ADR-013 for the reasoning.
- **Coverage**: JaCoCo is configured as a `check` goal in `pom.xml`. The build fails if line or branch coverage drops below 95%. Running `./mvnw verify` enforces this automatically.
- **Security**: error responses never expose internal technology names (ADR-012). Logs capture full details; clients receive generic messages.
- **Tag filtering**: uses OR semantics — a document matching any of the provided tags is returned (ADR-007).
- **Name search**: case-insensitive substring match (`LIKE %term%`) — ADR-008 discusses the trade-offs vs. full-text search.
- **OpenAPI docs**: all three endpoints are fully annotated with `@Operation`, `@ApiResponses`, and `@Parameter` — visible in Swagger UI.

