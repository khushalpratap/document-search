package com.document.search.search.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import com.document.search.search.domain.FacetValue;
import com.document.search.search.domain.SearchResponse;
import com.document.search.search.domain.SearchResult;
import com.example.search.common.IndexedDocument;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {
    private static final int MAX_RESULTS = 20;
    private static final int MAX_FACET_VALUES = 20;

    private final ElasticsearchOperations operations;

    public SearchService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    //@Cacheable(cacheNames = "search", key = "#tenantId + ':' + #query + ':' + #facetFields + ':' + #filters")
    public SearchResponse search(String tenantId, String query, List<String> facetFields, Map<String, String> filters) {

        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    // Exact/full-text match gets higher relevance
                    b.should(s -> s.multiMatch(mm -> mm
                            .query(query)
                            .fields("title^3", "content")
                            .operator(Operator.And)
                    ));
                    // Fuzzy match handles spelling mistakes
                    b.should(s -> s.multiMatch(mm -> mm
                            .query(query)
                            .fields("title^2", "content")
                            .fuzziness("AUTO")
                            .prefixLength(1)
                    ));
                    // Tenant isolation
                    b.filter(f -> f.term(t -> t
                            .field("tenantId")
                            .value(tenantId)
                    ));
                    // Facet filters narrow results to the selected metadata values
                    filters.forEach((field, value) -> b.filter(f -> f.term(t -> t
                            .field(metadataKeywordField(field))
                            .value(value)
                    )));
                    b.minimumShouldMatch("1");
                    return b;
                }))
                .withMaxResults(MAX_RESULTS)
                .withHighlightQuery(highlightQuery());

        facetFields.forEach(field -> builder.withAggregation(field, Aggregation.of(a -> a
                .terms(t -> t.field(metadataKeywordField(field)).size(MAX_FACET_VALUES))
        )));

        SearchHits<IndexedDocument> hits = operations.search(builder.build(), IndexedDocument.class);

        List<SearchResult> results = hits.stream().map(this::toResult).toList();
        Map<String, List<FacetValue>> facets = extractFacets(hits, facetFields);
        return new SearchResponse(results, facets);
    }

    private HighlightQuery highlightQuery() {
        Highlight highlight = new Highlight(List.of(new HighlightField("title"), new HighlightField("content")));
        return new HighlightQuery(highlight, IndexedDocument.class);
    }

    private String metadataKeywordField(String field) {
        return "metadata." + field + ".keyword";
    }

    private Map<String, List<FacetValue>> extractFacets(SearchHits<IndexedDocument> hits, List<String> facetFields) {
        if (facetFields.isEmpty()) return Map.of();

        AggregationsContainer<?> container = hits.getAggregations();
        if (!(container instanceof ElasticsearchAggregations aggregations)) return Map.of();

        Map<String, List<FacetValue>> result = new LinkedHashMap<>();
        for (String field : facetFields) {
            ElasticsearchAggregation aggregation = aggregations.aggregationsAsMap().get(field);
            if (aggregation == null) continue;

            StringTermsAggregate terms = aggregation.aggregation().getAggregate().sterms();
            List<FacetValue> values = terms.buckets().array().stream()
                    .map(bucket -> new FacetValue(bucket.key().stringValue(), bucket.docCount()))
                    .toList();
            result.put(field, values);
        }
        return result;
    }

    private SearchResult toResult(SearchHit<IndexedDocument> hit) {
        IndexedDocument d = hit.getContent();
        return new SearchResult(d.id(), d.title(), snippet(hit, d.content()), hit.getScore());
    }

    private String snippet(SearchHit<IndexedDocument> hit, String content) {
        List<String> highlighted = hit.getHighlightField("content");
        if (highlighted != null && !highlighted.isEmpty()) {
            return String.join(" ... ", highlighted);
        }
        if (content == null) return "";
        return content.length() <= 180 ? content : content.substring(0, 180) + "...";
    }
}
