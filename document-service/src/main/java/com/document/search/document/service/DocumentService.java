package com.document.search.document.service;

import com.document.search.common.DocumentEvent;
import com.document.search.document.domain.DocumentEntity;
import com.document.search.document.domain.DocumentRequest;
import com.document.search.document.domain.DocumentResponse;
import com.document.search.document.domain.OutboxEntity;
import com.document.search.document.repository.DocumentRepository;
import com.document.search.document.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class DocumentService {
    private final DocumentRepository repository;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public DocumentService(DocumentRepository repository,
                           OutboxRepository outbox,
                           ObjectMapper objectMapper) {
        this.repository = repository;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocumentResponse create(String tenantId, DocumentRequest request) {
        DocumentEntity entity = new DocumentEntity(tenantId, request.title(), request.content(), toJson(request.metadata()));
        repository.save(entity);
        publish(entity, DocumentEvent.EventType.CREATED);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(String tenantId, String id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .map(this::toResponse)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Transactional
    public DocumentResponse update(String tenantId, String id, DocumentRequest request) {
        DocumentEntity entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        entity.update(request.title(), request.content(), toJson(request.metadata()));
        publish(entity, DocumentEvent.EventType.UPDATED);
        return toResponse(entity);
    }

    @Transactional
    public void delete(String tenantId, String id) {
        DocumentEntity entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        repository.delete(entity);
        publish(entity, DocumentEvent.EventType.DELETED);
    }

    // Records the event in the outbox within the caller's transaction;
    // OutboxPublisher delivers it to Kafka after commit.
    private void publish(DocumentEntity e, DocumentEvent.EventType type) {
        DocumentEvent event = new DocumentEvent(
                type, e.getId(), e.getTenantId(), e.getTitle(), e.getContent(),
                fromJson(e.getMetadataJson()), e.getCreatedAt(), e.getUpdatedAt());
        try {
            outbox.save(new OutboxEntity(e.getId(), objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize document event", ex);
        }
    }

    private DocumentResponse toResponse(DocumentEntity e) {
        return new DocumentResponse(e.getId(), e.getTenantId(), e.getTitle(), e.getContent(),
                fromJson(e.getMetadataJson()), e.getCreatedAt(), e.getUpdatedAt());
    }

    private String toJson(Map<String, Object> value) {
        try {
            return value == null ? "{}" : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid metadata", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String value) {
        try {
            return value == null ? Map.of() : objectMapper.readValue(value, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public static class DocumentNotFoundException extends RuntimeException {
        public DocumentNotFoundException(String id) {
            super("Document not found: " + id);
        }
    }
}
