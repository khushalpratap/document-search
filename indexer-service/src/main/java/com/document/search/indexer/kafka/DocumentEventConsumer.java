package com.document.search.indexer.kafka;

import com.document.search.common.DocumentEvent;
import com.document.search.indexer.service.IndexService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentEventConsumer {
    private final IndexService indexService;

    public DocumentEventConsumer(IndexService indexService) {
        this.indexService = indexService;
    }

    @KafkaListener(topics = "${app.kafka.topic:document-events}", groupId = "document-indexer")
    public void consume(DocumentEvent event) {
        indexService.apply(event);
    }
}
