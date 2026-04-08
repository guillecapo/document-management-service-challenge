package com.clara.ops.challenge.document_management_service_challenge.infrastructure.adapter.in.web.dto;

import java.util.List;

public record DocumentSearchFiltersRequest(String user, String name, List<String> tags) {}
