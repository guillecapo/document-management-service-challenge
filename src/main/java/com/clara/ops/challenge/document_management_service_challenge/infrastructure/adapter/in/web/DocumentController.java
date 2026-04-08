package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.in.web;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentSearchCriteria;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentUpload;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.PagedResult;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.in.DownloadDocumentUseCase;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.in.SearchDocumentsUseCase;
import com.clara.ops.challenge.document_management_service_challenge.domain.port.in.UploadDocumentUseCase;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.in.web.dto.DocumentSearchFiltersRequest;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.in.web.dto.DownloadUrlResponse;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.in.web.dto.PaginatedDocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.in.web.dto.UploadDocumentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Document Management", description = "Upload, search and download PDF documents")
@RestController
@RequestMapping("/document-management")
@RequiredArgsConstructor
@Validated
public class DocumentController {

  private final UploadDocumentUseCase uploadDocumentUseCase;
  private final SearchDocumentsUseCase searchDocumentsUseCase;
  private final DownloadDocumentUseCase downloadDocumentUseCase;

  @Operation(
      summary = "Upload a PDF document",
      description = "Uploads a PDF file along with its metadata (user, name, tags).")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Document uploaded successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request (missing or blank fields, malformed multipart)",
        content = @Content(schema = @Schema(example = "{\"error\": \"user: must not be blank\"}"))),
    @ApiResponse(
        responseCode = "415",
        description = "Unsupported media type",
        content =
            @Content(
                schema = @Schema(example = "{\"error\": \"Unsupported media type: text/plain\"}"))),
    @ApiResponse(
        responseCode = "422",
        description = "File exceeds the 500MB limit",
        content =
            @Content(
                schema =
                    @Schema(
                        example =
                            "{\"error\": \"File size exceeds the maximum allowed limit of 500MB.\"}"))),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected storage failure",
        content =
            @Content(
                schema =
                    @Schema(
                        example =
                            "{\"error\": \"Storage operation failed. Please try again later.\"}"))),
    @ApiResponse(
        responseCode = "503",
        description = "Storage service (MinIO) unavailable",
        content =
            @Content(
                schema =
                    @Schema(example = "{\"error\": \"A required service is currently unavailable. Please try again later.\"}")))
  })
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public void upload(
      @Parameter(description = "Document metadata (user, name, tags)", required = true)
          @RequestPart("metadata")
          @Valid
          UploadDocumentRequest metadata,
      @Parameter(description = "PDF file to upload", required = true) @RequestPart("file")
          MultipartFile file)
      throws IOException {

    DocumentUpload upload =
        new DocumentUpload(
            metadata.user(),
            metadata.name(),
            metadata.tags(),
            file.getInputStream(),
            file.getSize(),
            file.getContentType());

    uploadDocumentUseCase.upload(upload);
  }

  @Operation(
      summary = "Search documents",
      description =
          "Returns a paginated list of documents filtered by user, name (partial match), and/or tags (OR logic). All filters are optional.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Search results returned"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid pagination parameters",
        content = @Content(schema = @Schema(example = "{\"error\": \"page: must be >= 0\"}"))),
    @ApiResponse(
        responseCode = "503",
        description = "Database unavailable",
        content =
            @Content(
                schema =
                    @Schema(example = "{\"error\": \"A required service is currently unavailable. Please try again later.\"}")))
  })
  @PostMapping("/search")
  public PaginatedDocumentResponse search(
      @RequestBody DocumentSearchFiltersRequest filters,
      @Parameter(description = "Zero-based page index", example = "0")
          @RequestParam(defaultValue = "0")
          @Min(0)
          int page,
      @Parameter(description = "Number of items per page", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(1)
          int size) {

    DocumentSearchCriteria criteria =
        new DocumentSearchCriteria(filters.user(), filters.name(), filters.tags());

    PagedResult<Document> result = searchDocumentsUseCase.search(criteria, page, size);

    return PaginatedDocumentResponse.from(result);
  }

  @Operation(
      summary = "Get download URL",
      description = "Returns a presigned MinIO URL to download the document. The URL expires after the configured TTL.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Presigned URL generated"),
    @ApiResponse(
        responseCode = "404",
        description = "Document not found",
        content =
            @Content(
                schema =
                    @Schema(example = "{\"error\": \"Document not found with id: 01J...\"}"))),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected failure generating the URL",
        content =
            @Content(
                schema =
                    @Schema(
                        example =
                            "{\"error\": \"Storage operation failed. Please try again later.\"}"))),
    @ApiResponse(
        responseCode = "503",
        description = "Storage service (MinIO) unavailable",
        content =
            @Content(
                schema =
                    @Schema(example = "{\"error\": \"A required service is currently unavailable. Please try again later.\"}")))
  })
  @GetMapping("/download/{documentId}")
  public DownloadUrlResponse download(
      @Parameter(description = "ULID identifier of the document", required = true)
          @PathVariable
          String documentId) {
    return new DownloadUrlResponse(downloadDocumentUseCase.generateDownloadUrl(documentId));
  }
}
