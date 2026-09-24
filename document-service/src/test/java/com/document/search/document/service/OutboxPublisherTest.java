package com.document.search.document.service;

import com.document.search.common.DocumentEvent;
import com.document.search.document.domain.DocumentRequest;
import com.document.search.document.domain.DocumentResponse;
import com.document.search.document.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.datasource.url=jdbc:h2:mem:outboxdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.outbox.poll-interval-ms=3600000"
})
class OutboxPublisherTest {
    private static final String TENANT = "tenant-a";

    @Autowired DocumentService documents;
    @Autowired OutboxPublisher publisher;
    @Autowired OutboxRepository outbox;
    @MockitoBean KafkaTemplate<String, DocumentEvent> kafkaTemplate;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        reset(kafkaTemplate);
    }

    @Test
    void writesStayInOutboxUntilPublishedInOrder() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        DocumentResponse created = documents.create(TENANT, new DocumentRequest("t1", "c1", Map.of("priority", 3)));
        documents.update(TENANT, created.id(), new DocumentRequest("t2", "c2", Map.of()));
        documents.delete(TENANT, created.id());

        verifyNoInteractions(kafkaTemplate);
        assertThat(outbox.count()).isEqualTo(3);

        publisher.publishPending();

        ArgumentCaptor<DocumentEvent> events = ArgumentCaptor.forClass(DocumentEvent.class);
        verify(kafkaTemplate, times(3)).send(eq("document-events"), eq(created.id()), events.capture());
        assertThat(events.getAllValues()).extracting(DocumentEvent::type).containsExactly(
                DocumentEvent.EventType.CREATED, DocumentEvent.EventType.UPDATED, DocumentEvent.EventType.DELETED);
        assertThat(events.getAllValues().get(0).metadata()).containsEntry("priority", 3);
        assertThat(events.getAllValues().get(1).title()).isEqualTo("t2");
        assertThat(outbox.count()).isZero();
    }

    @Test
    void failedSendKeepsThatEventAndEverythingAfterIt() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        documents.create(TENANT, new DocumentRequest("a", "a", null));
        documents.create(TENANT, new DocumentRequest("b", "b", null));
        documents.create(TENANT, new DocumentRequest("c", "c", null));

        publisher.publishPending();

        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any());
        assertThat(outbox.count()).isEqualTo(2);
    }
}
