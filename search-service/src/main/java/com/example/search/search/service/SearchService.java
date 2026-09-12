package com.example.search.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import com.example.search.search.domain.IndexedDocument;
import com.example.search.search.domain.SearchResult;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class SearchService {
    private final ElasticsearchOperations operations;
    public SearchService(ElasticsearchOperations operations) { this.operations = operations; }

    @Cacheable(cacheNames = "search", key = "#tenantId + ':' + #query")
    public List<SearchResult> search(String tenantId, String query) {

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b

                        // Exact/full-text match gets higher relevance
                        .should(s -> s.multiMatch(mm -> mm
                                .query(query)
                                .fields("title^3", "content")
                                .operator(Operator.And)
                        ))

                        // Fuzzy match handles spelling mistakes
                        .should(s -> s.multiMatch(mm -> mm
                                .query(query)
                                .fields("title^2", "content")
                                .fuzziness("AUTO")
                                .prefixLength(1)
                        ))

                        // Tenant isolation
                        .filter(f -> f.term(t -> t
                                .field("tenantId")
                                .value(tenantId)
                        ))

                        .minimumShouldMatch("1")
                ))
                .withMaxResults(20)
                .build();

        return operations.search(nativeQuery, IndexedDocument.class)
                .stream()
                .map(this::toResult)
                .toList();
    }

    private SearchResult toResult(SearchHit<IndexedDocument> hit) {
        IndexedDocument d = hit.getContent();
        return new SearchResult(d.id(), d.title(), snippet(d.content()), hit.getScore());
    }

    private String snippet(String content) {
        if (content == null) return "";
        return content.length() <= 180 ? content : content.substring(0, 180) + "...";
    }
}
