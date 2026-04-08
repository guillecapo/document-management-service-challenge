package com.clara.ops.challenge.document_management_service_challenge.domain.model;

import java.util.List;

public record PagedResult<T>(
    List<T> items, int currentPage, int itemsPerPage, int totalPages, long totalItems) {}
