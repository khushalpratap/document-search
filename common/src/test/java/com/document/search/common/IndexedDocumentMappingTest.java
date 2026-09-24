package com.document.search.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.index.MappingBuilder;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;

import static org.assertj.core.api.Assertions.assertThat;

class IndexedDocumentMappingTest {

    @Test
    void metadataScalarsGetKeywordSubField() throws Exception {
        String mapping = new MappingBuilder(new MappingElasticsearchConverter(new SimpleElasticsearchMappingContext()))
                .buildPropertyMapping(IndexedDocument.class);

        JsonNode templates = new ObjectMapper().readTree(mapping).get("dynamic_templates");
        assertThat(templates).isNotNull();
        assertThat(templates.findValuesAsText("match_mapping_type"))
                .containsExactlyInAnyOrder("string", "date", "long", "double", "boolean");
        templates.findValues("mapping").forEach(m ->
                assertThat(m.at("/fields/keyword/type").asText()).isEqualTo("keyword"));
    }
}
