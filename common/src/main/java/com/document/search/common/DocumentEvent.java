package com.document.search.common;

import java.time.Instant;
import java.util.Map;

public record DocumentEvent(
        EventType type,
        String documentId,
        String tenantId,
        String title,
        String content,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public enum EventType {CREATED, UPDATED, DELETED}
}
