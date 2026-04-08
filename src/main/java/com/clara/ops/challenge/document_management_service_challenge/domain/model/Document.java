package com.clara.ops.challenge.document_management_service_challenge.domain.model;

import java.time.LocalDateTime;
import java.util.List;

public record Document(
    String id,
    String user,
    String name,
    List<String> tags,
    String storagePath,
    Long fileSize,
    String fileType,
    LocalDateTime createdAt) {}
