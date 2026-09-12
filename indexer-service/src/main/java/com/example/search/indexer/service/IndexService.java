package com.example.search.indexer.service;

import com.example.search.common.DocumentEvent;
import com.example.search.indexer.domain.IndexedDocument;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

@Service
public class IndexService {
    private final ElasticsearchOperations operations;
    public IndexService(ElasticsearchOperations operations) { this.operations = operations; }

    public void apply(DocumentEvent event) {
        if (event.type() == DocumentEvent.EventType.DELETED) {
            operations.delete(event.documentId(), IndexedDocument.class);
            return;
        }
        operations.save(new IndexedDocument(event.documentId(), event.tenantId(), event.title(),
                event.content(), event.metadata(), event.createdAt(), event.updatedAt()));
    }
}
