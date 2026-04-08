package com.clara.ops.challenge.document_management_service_challenge.domain.exception;

public class StorageException extends RuntimeException {

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
