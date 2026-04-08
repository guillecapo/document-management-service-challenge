package com.clara.ops.challenge.document_management_service_challenge.domain.exception;

public class DependencyUnavailableException extends RuntimeException {

  public DependencyUnavailableException(String dependency, Throwable cause) {
    super("Dependency unavailable: " + dependency, cause);
  }
}
