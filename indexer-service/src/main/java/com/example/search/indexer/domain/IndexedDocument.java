package com.example.search.indexer.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.Map;

@Document(indexName = "documents", createIndex = true)
public record IndexedDocument(
        @Id String id,
        @Field(type = FieldType.Keyword) String tenantId,
        @Field(type = FieldType.Text) String title,
        @Field(type = FieldType.Text) String content,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {}
