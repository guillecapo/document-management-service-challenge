package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.in.web;

import com.clara.ops.challenge.document_management_service_challenge.domain.exception.DependencyUnavailableException;
import com.clara.ops.challenge.document_management_service_challenge.domain.exception.DocumentNotFoundException;
import com.clara.ops.challenge.document_management_service_challenge.domain.exception.StorageException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(DocumentNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> handleNotFound(DocumentNotFoundException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(DependencyUnavailableException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public Map<String, String> handleDependencyUnavailable(DependencyUnavailableException ex) {
    log.error("Dependency unavailable: {}", ex.getMessage(), ex);
    return Map.of("error", "A required service is currently unavailable. Please try again later.");
  }

  @ExceptionHandler(DataAccessException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public Map<String, String> handleDataAccess(DataAccessException ex) {
    log.error("Database unavailable: {}", ex.getMessage(), ex);
    return Map.of("error", "A required service is currently unavailable. Please try again later.");
  }

  @ExceptionHandler(StorageException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Map<String, String> handleStorage(StorageException ex) {
    log.error("Storage failure: {}", ex.getMessage(), ex);
    return Map.of("error", "Storage operation failed. Please try again later.");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public Map<String, String> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
    return Map.of("error", "File size exceeds the maximum allowed limit of 500MB.");
  }

  @ExceptionHandler(MultipartException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleMultipart(MultipartException ex) {
    return Map.of("error", "Invalid multipart request: " + ex.getMessage());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
  public Map<String, String> handleMediaType(HttpMediaTypeNotSupportedException ex) {
    return Map.of("error", "Unsupported media type: " + ex.getContentType());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> handleNoResource(NoResourceFoundException ex) {
    return Map.of("error", "Endpoint not found: " + ex.getResourcePath());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .findFirst()
            .orElse("Invalid request");
    return Map.of("error", message);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleConstraintViolation(ConstraintViolationException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Map<String, String> handleGeneric(Exception ex) {
    log.error("Unexpected error: {}", ex.getMessage(), ex);
    return Map.of("error", "An unexpected error occurred.");
  }
}
