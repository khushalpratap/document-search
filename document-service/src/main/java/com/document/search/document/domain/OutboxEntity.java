package com.document.search.document.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A document event waiting to be published to Kafka. Written in the same
 * transaction as the document change, so the two can never diverge.
 */
@Entity
@Table(name = "document_outbox")
public class OutboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "document_id", nullable = false)
    private String documentId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(nullable = false)
    private Instant createdAt;

    protected OutboxEntity() {}

    public OutboxEntity(String documentId, String payload) {
        this.documentId = documentId;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getDocumentId() { return documentId; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
