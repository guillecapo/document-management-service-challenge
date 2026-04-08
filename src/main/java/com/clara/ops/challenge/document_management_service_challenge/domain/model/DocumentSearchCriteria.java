package com.clara.ops.challenge.document_management_service_challenge.domain.model;

import java.util.List;

public record DocumentSearchCriteria(String user, String name, List<String> tags) {}
