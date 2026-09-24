package com.document.search.common;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.DynamicTemplates;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.Map;

// Every scalar metadata value (string, number, boolean, date) is mapped as text plus a
// "keyword" sub-field, so search-service can filter and facet on metadata.<field>.keyword
// whatever the value's JSON type.
@Document(indexName = "documents", createIndex = false)
@DynamicTemplates(mappingPath = "/elasticsearch/document-dynamic-templates.json")
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
