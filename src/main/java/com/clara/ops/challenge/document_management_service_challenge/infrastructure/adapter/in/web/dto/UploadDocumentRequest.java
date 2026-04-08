package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UploadDocumentRequest(
    @NotBlank String user, @NotBlank String name, @NotEmpty List<String> tags) {}
