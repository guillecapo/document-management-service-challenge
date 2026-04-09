package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentSearchCriteria;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentUpload;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.PagedResult;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.in.DownloadDocumentUseCase;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.in.SearchDocumentsUseCase;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.in.UploadDocumentUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies DocumentController's request mapping, input validation, and response serialization.
 * Uses @WebMvcTest to load only the web layer; use cases are mocked so no database or storage
 * is required. Exception-to-status mapping is covered separately in GlobalExceptionHandlerTest.
 */
@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private UploadDocumentUseCase uploadDocumentUseCase;
  @MockitoBean private SearchDocumentsUseCase searchDocumentsUseCase;
  @MockitoBean private DownloadDocumentUseCase downloadDocumentUseCase;

  // ---------------------------------------------------------------------------
  // POST /document-management/upload
  // ---------------------------------------------------------------------------

  /**
   * A valid multipart request with metadata (user, name, tags) and a file part must be accepted
   * and result in a 201. The controller must delegate to the upload use case exactly once.
   */
  @Test
  @DisplayName("POST /upload with valid multipart returns 201")
  void upload_validRequest_returns201() throws Exception {
    MockPart metadataPart =
        new MockPart(
            "metadata",
            objectMapper
                .writeValueAsBytes(Map.of("user", "alice", "name", "contract.pdf", "tags",
                    List.of("legal"))));
    metadataPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    MockPart filePart = new MockPart("file", "contract.pdf", new byte[]{1, 2, 3});
    filePart.getHeaders().setContentType(MediaType.APPLICATION_PDF);

    mockMvc
        .perform(multipart("/document-management/upload").part(metadataPart).part(filePart))
        .andExpect(status().isCreated());

    verify(uploadDocumentUseCase).upload(any(DocumentUpload.class));
  }

  /**
   * Upload metadata with a blank user field must fail validation before reaching the use case,
   * returning 400 with a field error message.
   */
  @Test
  @DisplayName("POST /upload with blank user returns 400")
  void upload_blankUser_returns400() throws Exception {
    MockPart metadataPart =
        new MockPart(
            "metadata",
            objectMapper
                .writeValueAsBytes(Map.of("user", "", "name", "contract.pdf", "tags",
                    List.of("legal"))));
    metadataPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    MockPart filePart = new MockPart("file", "contract.pdf", new byte[]{1, 2, 3});
    filePart.getHeaders().setContentType(MediaType.APPLICATION_PDF);

    mockMvc
        .perform(multipart("/document-management/upload").part(metadataPart).part(filePart))
        .andExpect(status().isBadRequest());
  }

  /**
   * Upload metadata with a blank name field must fail @NotBlank validation and return 400.
   */
  @Test
  @DisplayName("POST /upload with blank name returns 400")
  void upload_blankName_returns400() throws Exception {
    MockPart metadataPart =
        new MockPart(
            "metadata",
            objectMapper
                .writeValueAsBytes(Map.of("user", "alice", "name", "", "tags",
                    List.of("legal"))));
    metadataPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    MockPart filePart = new MockPart("file", "contract.pdf", new byte[]{1, 2, 3});
    filePart.getHeaders().setContentType(MediaType.APPLICATION_PDF);

    mockMvc
        .perform(multipart("/document-management/upload").part(metadataPart).part(filePart))
        .andExpect(status().isBadRequest());
  }

  /**
   * Upload metadata with an empty tags list must fail @NotEmpty validation and return 400.
   */
  @Test
  @DisplayName("POST /upload with empty tags returns 400")
  void upload_emptyTags_returns400() throws Exception {
    MockPart metadataPart =
        new MockPart(
            "metadata",
            objectMapper
                .writeValueAsBytes(Map.of("user", "alice", "name", "contract.pdf", "tags",
                    List.of())));
    metadataPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    MockPart filePart = new MockPart("file", "contract.pdf", new byte[]{1, 2, 3});
    filePart.getHeaders().setContentType(MediaType.APPLICATION_PDF);

    mockMvc
        .perform(multipart("/document-management/upload").part(metadataPart).part(filePart))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------------------
  // POST /document-management/search
  // ---------------------------------------------------------------------------

  /**
   * A search request with no filters must return 200 with a correctly structured paginated
   * response. Verifies that metadata fields and document list are serialized as expected.
   */
  @Test
  @DisplayName("POST /search with no filters returns 200 with paginated response")
  void search_noFilters_returns200WithPaginatedResponse() throws Exception {
    Document doc =
        new Document(
            "01ARZ3NDEKTSV4RRFFQ69G5FAV",
            "alice",
            "contract.pdf",
            List.of("legal"),
            "alice/contract.pdf",
            1024L,
            "application/pdf",
            LocalDateTime.of(2026, 1, 15, 10, 0));

    PagedResult<Document> pagedResult = new PagedResult<>(List.of(doc), 0, 20, 1, 1L);
    when(searchDocumentsUseCase.search(any(), eq(0), eq(20))).thenReturn(pagedResult);

    mockMvc
        .perform(
            post("/document-management/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.currentPage").value(0))
        .andExpect(jsonPath("$.metadata.itemsPerPage").value(20))
        .andExpect(jsonPath("$.metadata.totalItems").value(1))
        .andExpect(jsonPath("$.documents[0].id").value("01ARZ3NDEKTSV4RRFFQ69G5FAV"))
        .andExpect(jsonPath("$.documents[0].user").value("alice"))
        .andExpect(jsonPath("$.documents[0].name").value("contract.pdf"));
  }

  /**
   * A search request with user, name and tags filters must map them to DocumentSearchCriteria
   * exactly. Verifies that the controller does not drop or alter any filter value.
   */
  @Test
  @DisplayName("POST /search with filters delegates correct criteria to use case")
  void search_withFilters_delegatesCorrectCriteria() throws Exception {
    PagedResult<Document> emptyPage = new PagedResult<>(List.of(), 0, 20, 0, 0L);
    when(searchDocumentsUseCase.search(any(), eq(0), eq(20))).thenReturn(emptyPage);

    mockMvc
        .perform(
            post("/document-management/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"user\":\"alice\",\"name\":\"contract\",\"tags\":[\"legal\",\"finance\"]}"))
        .andExpect(status().isOk());

    ArgumentCaptor<DocumentSearchCriteria> captor =
        ArgumentCaptor.forClass(DocumentSearchCriteria.class);
    verify(searchDocumentsUseCase).search(captor.capture(), eq(0), eq(20));

    DocumentSearchCriteria captured = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(captured.user()).isEqualTo("alice");
    org.assertj.core.api.Assertions.assertThat(captured.name()).isEqualTo("contract");
    org.assertj.core.api.Assertions.assertThat(captured.tags())
        .containsExactly("legal", "finance");
  }

  /**
   * A negative page parameter violates the @Min(0) constraint on the controller method.
   * The handler must return 400 without invoking the use case.
   */
  @Test
  @DisplayName("POST /search with negative page returns 400")
  void search_negativePage_returns400() throws Exception {
    mockMvc
        .perform(
            post("/document-management/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .param("page", "-1"))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------------------
  // GET /document-management/download/{documentId}
  // ---------------------------------------------------------------------------

  /**
   * A download request for an existing document must return 200 with the presigned URL in the
   * response body under the "url" field.
   */
  @Test
  @DisplayName("GET /download/{documentId} returns 200 with presigned URL")
  void download_existingDocument_returns200WithUrl() throws Exception {
    String presignedUrl =
        "http://localhost:9000/documents/alice/contract.pdf?X-Amz-Signature=abc";
    when(downloadDocumentUseCase.generateDownloadUrl("01ARZ3NDEKTSV4RRFFQ69G5FAV"))
        .thenReturn(presignedUrl);

    mockMvc
        .perform(get("/document-management/download/01ARZ3NDEKTSV4RRFFQ69G5FAV"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value(presignedUrl));
  }
}
