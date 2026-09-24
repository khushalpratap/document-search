package com.document.search.indexer.service;

import com.document.search.common.DocumentEvent;
import com.document.search.common.IndexedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

@Service
public class IndexService {
    private static final Logger log = LoggerFactory.getLogger(IndexService.class);

    private final ElasticsearchOperations operations;

    public IndexService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public void apply(DocumentEvent event) {
        if (event.type() == DocumentEvent.EventType.DELETED) {
            delete(event);
            return;
        }
        operations.save(new IndexedDocument(event.documentId(), event.tenantId(), event.title(),
                event.content(), event.metadata(), event.createdAt(), event.updatedAt()));
    }

    // Only delete when the indexed copy belongs to the event's tenant, so a malformed
    // or replayed event can never remove another tenant's document.
    private void delete(DocumentEvent event) {
        IndexedDocument existing = operations.get(event.documentId(), IndexedDocument.class);
        if (existing == null) return;
        if (!existing.tenantId().equals(event.tenantId())) {
            log.warn("Ignoring delete of document {}: event tenant {} does not own it",
                    event.documentId(), event.tenantId());
            return;
        }
        operations.delete(event.documentId(), IndexedDocument.class);
    }
}
