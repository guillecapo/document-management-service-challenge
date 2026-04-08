package com.clara.ops.challenge.document_management_service_challenge.domain.model;

import java.io.InputStream;
import java.util.List;

public record DocumentUpload(
    String user,
    String name,
    List<String> tags,
    InputStream fileStream,
    long fileSize,
    String fileType) {}
