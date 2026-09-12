package com.example.search.document.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_documents_tenant", columnList = "tenant_id")
})
public class DocumentEntity {
    @Id
    private String id;
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(columnDefinition = "TEXT")
    private String metadataJson;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected DocumentEntity() {}

    public DocumentEntity(String tenantId, String title, String content, String metadataJson) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.title = title;
        this.content = content;
        this.metadataJson = metadataJson;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String title, String content, String metadataJson) {
        this.title = title;
        this.content = content;
        this.metadataJson = metadataJson;
        this.updatedAt = Instant.now();
    }
}
