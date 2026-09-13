package com.document.search.common;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.Map;

@Document(indexName = "documents", createIndex = false)
public record IndexedDocument(
        @Id String id,
        @Field(type = FieldType.Keyword) String tenantId,
        @Field(type = FieldType.Text) String title,
        @Field(type = FieldType.Text) String content,
        Map<String, Object> metadata,
        @Field(type = FieldType.Date, format = DateFormat.date_time) Instant createdAt,
        @Field(type = FieldType.Date, format = DateFormat.date_time) Instant updatedAt
) {
}
