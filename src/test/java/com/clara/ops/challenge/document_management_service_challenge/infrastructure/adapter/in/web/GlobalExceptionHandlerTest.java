package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clara.ops.challenge.document_management_service_challenge.domain.exception.DependencyUnavailableException;
import com.clara.ops.challenge.document_management_service_challenge.domain.exception.DocumentNotFoundException;
import com.clara.ops.challenge.document_management_service_challenge.domain.exception.StorageException;
import jakarta.validation.ConstraintViolationException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Verifies that GlobalExceptionHandler maps each exception type to the correct HTTP status and
 * response body. Uses MockMvc standalone with a dedicated ThrowingController that fires each
 * exception in isolation, so every handler is exercised independently without loading the full
 * Spring context.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  // ---------------------------------------------------------------------------
  // Domain exceptions
  // ---------------------------------------------------------------------------

  @Test
  void handleNotFound_returns404WithExceptionMessage() throws Exception {
    mockMvc
        .perform(get("/throw/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Document not found with id: doc-123"));
  }

  @Test
  void handleDependencyUnavailable_returns503WithFixedMessage() throws Exception {
    mockMvc
        .perform(get("/throw/dependency-unavailable"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            jsonPath("$.error")
                .value("A required service is currently unavailable. Please try again later."));
  }

  // ---------------------------------------------------------------------------
  // Infrastructure / database exceptions
  // ---------------------------------------------------------------------------

  /**
   * DataAccessException signals that the database is unreachable or unusable. The handler treats
   * this the same as DependencyUnavailableException so the caller gets a consistent 503.
   */
  @Test
  void handleDataAccess_returns503WithFixedMessage() throws Exception {
    mockMvc
        .perform(get("/throw/data-access"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            jsonPath("$.error")
                .value("A required service is currently unavailable. Please try again later."));
  }

  @Test
  void handleStorage_returns500WithFixedMessage() throws Exception {
    mockMvc
        .perform(get("/throw/storage"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("Storage operation failed. Please try again later."));
  }

  // ---------------------------------------------------------------------------
  // Multipart / file upload exceptions
  // ---------------------------------------------------------------------------

  /**
   * MaxUploadSizeExceededException is a subtype of MultipartException. The more-specific handler
   * must fire first and return 422 instead of 400.
   */
  @Test
  void handleMaxUploadSize_returns422WithFixedMessage() throws Exception {
    mockMvc
        .perform(get("/throw/max-upload-size"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.error").value("File size exceeds the maximum allowed limit of 500MB."));
  }

  @Test
  void handleMultipart_returns400WithExceptionMessage() throws Exception {
    mockMvc
        .perform(get("/throw/multipart"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Invalid multipart request: bad multipart"));
  }

  // ---------------------------------------------------------------------------
  // HTTP protocol exceptions
  // ---------------------------------------------------------------------------

  @Test
  void handleMediaType_returns415WithContentType() throws Exception {
    mockMvc
        .perform(get("/throw/media-type"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.error").value("Unsupported media type: text/plain"));
  }

  @Test
  void handleNoResource_returns404WithResourcePath() throws Exception {
    mockMvc
        .perform(get("/throw/no-resource"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Endpoint not found: /unknown/path"));
  }

  // ---------------------------------------------------------------------------
  // Validation exceptions
  // ---------------------------------------------------------------------------

  /**
   * The handler surfaces only the first field error so the client gets a single, actionable
   * message rather than a raw Spring validation dump.
   */
  @Test
  void handleMethodArgNotValid_returns400WithFirstFieldError() throws Exception {
    mockMvc
        .perform(get("/throw/method-arg-not-valid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("name: must not be blank"));
  }

  @Test
  void handleConstraintViolation_returns400WithViolationMessage() throws Exception {
    mockMvc
        .perform(get("/throw/constraint-violation"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("name: must not be blank"));
  }

  // ---------------------------------------------------------------------------
  // Catch-all
  // ---------------------------------------------------------------------------

  @Test
  void handleGeneric_returns500WithFixedMessage() throws Exception {
    mockMvc
        .perform(get("/throw/generic"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("An unexpected error occurred."));
  }

  // ---------------------------------------------------------------------------
  // Dummy controller — one endpoint per exception type
  // ---------------------------------------------------------------------------

  /**
   * Each mapping exists solely to throw a specific exception so the advice handler under test
   * receives it in isolation. No business logic lives here.
   */
  @RestController
  static class ThrowingController {

    @GetMapping("/throw/not-found")
    void throwNotFound() {
      throw new DocumentNotFoundException("doc-123");
    }

    @GetMapping("/throw/dependency-unavailable")
    void throwDependencyUnavailable() {
      throw new DependencyUnavailableException("MinIO", new RuntimeException("connection refused"));
    }

    @GetMapping("/throw/data-access")
    void throwDataAccess() {
      throw new DataIntegrityViolationException("db error");
    }

    @GetMapping("/throw/storage")
    void throwStorage() {
      throw new StorageException("upload failed", new RuntimeException("io error"));
    }

    @GetMapping("/throw/max-upload-size")
    void throwMaxUploadSize() {
      throw new MaxUploadSizeExceededException(500L);
    }

    @GetMapping("/throw/multipart")
    void throwMultipart() {
      throw new MultipartException("bad multipart");
    }

    @GetMapping("/throw/media-type")
    void throwMediaType() throws HttpMediaTypeNotSupportedException {
      throw new HttpMediaTypeNotSupportedException(
          MediaType.TEXT_PLAIN, List.of(MediaType.MULTIPART_FORM_DATA));
    }

    @GetMapping("/throw/no-resource")
    void throwNoResource() throws NoResourceFoundException {
      throw new NoResourceFoundException(HttpMethod.GET, "/unknown/path");
    }

    @GetMapping("/throw/method-arg-not-valid")
    void throwMethodArgNotValid() throws Exception {
      Method method = ThrowingController.class.getDeclaredMethod("throwMethodArgNotValid");
      MethodParameter param = new MethodParameter(method, -1);
      BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "searchRequest");
      bindingResult.addError(new FieldError("searchRequest", "name", "must not be blank"));
      throw new MethodArgumentNotValidException(param, bindingResult);
    }

    @GetMapping("/throw/constraint-violation")
    void throwConstraintViolation() {
      throw new ConstraintViolationException("name: must not be blank", Set.of());
    }

    @GetMapping("/throw/generic")
    void throwGeneric() {
      throw new RuntimeException("unexpected");
    }
  }
}
