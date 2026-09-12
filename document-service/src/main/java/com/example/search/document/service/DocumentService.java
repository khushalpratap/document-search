package com.example.search.document.service;

import com.example.search.common.DocumentEvent;
import com.example.search.document.domain.*;
import com.example.search.document.repository.DocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class DocumentService {
    private final DocumentRepository repository;
    private final KafkaTemplate<String, DocumentEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public DocumentService(DocumentRepository repository,
                           KafkaTemplate<String, DocumentEvent> kafkaTemplate,
                           ObjectMapper objectMapper,
                           @Value("${app.kafka.topic:document-events}") String topic) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
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
    public void delete(String tenantId, String id) {
        DocumentEntity entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        repository.delete(entity);
        publish(entity, DocumentEvent.EventType.DELETED);
    }

    private void publish(DocumentEntity e, DocumentEvent.EventType type) {
        kafkaTemplate.send(topic, e.getId(), new DocumentEvent(
                type, e.getId(), e.getTenantId(), e.getTitle(), e.getContent(),
                fromJson(e.getMetadataJson()), e.getCreatedAt(), e.getUpdatedAt()));
    }

    private DocumentResponse toResponse(DocumentEntity e) {
        return new DocumentResponse(e.getId(), e.getTenantId(), e.getTitle(), e.getContent(),
                fromJson(e.getMetadataJson()), e.getCreatedAt(), e.getUpdatedAt());
    }

    private String toJson(Map<String, Object> value) {
        try { return value == null ? "{}" : objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid metadata", ex); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String value) {
        try { return value == null ? Map.of() : objectMapper.readValue(value, Map.class); }
        catch (Exception ex) { return Map.of(); }
    }

    public static class DocumentNotFoundException extends RuntimeException {
        public DocumentNotFoundException(String id) { super("Document not found: " + id); }
    }
}
