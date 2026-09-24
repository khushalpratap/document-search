package com.document.search.document.service;

import com.document.search.common.DocumentEvent;
import com.document.search.document.domain.OutboxEntity;
import com.document.search.document.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Relays outbox rows to Kafka and deletes them once the broker acknowledges.
 * Delivery is at-least-once: a crash between send and commit re-sends the row,
 * which the indexer tolerates because its writes are idempotent.
 */
@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, DocumentEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final int batchSize;

    public OutboxPublisher(OutboxRepository outbox,
                           KafkaTemplate<String, DocumentEvent> kafkaTemplate,
                           ObjectMapper objectMapper,
                           @Value("${app.kafka.topic:document-events}") String topic,
                           @Value("${app.outbox.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEntity> batch = outbox.findBatchForPublishing(PageRequest.of(0, batchSize));
        for (OutboxEntity row : batch) {
            try {
                DocumentEvent event = objectMapper.readValue(row.getPayload(), DocumentEvent.class);
                kafkaTemplate.send(topic, row.getDocumentId(), event).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                outbox.delete(row);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                // Stop at the first failure so later events are never sent ahead of it;
                // rows already sent in this batch are still deleted on commit.
                log.warn("Outbox publish failed for row {} (document {}); will retry", row.getId(), row.getDocumentId(), ex);
                return;
            }
        }
    }
}
