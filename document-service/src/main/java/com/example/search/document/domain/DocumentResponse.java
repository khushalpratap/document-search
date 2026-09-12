package com.example.search.document.domain;

import java.time.Instant;
import java.util.Map;

public record DocumentResponse(
        String id,
        String tenantId,
        String title,
        String content,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {}
