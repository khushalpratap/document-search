package com.document.search.search.controller;

import com.document.search.common.TenantHeaders;
import com.document.search.search.domain.SearchResponse;
import com.document.search.search.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/search")
public class SearchController {
    private static final int MAX_FACET_FIELDS = 10;

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @GetMapping
    public SearchResponse search(@RequestHeader(TenantHeaders.TENANT_ID) String tenantId,
                                 @RequestParam("q") String query,
                                 @RequestParam(value = "facets", required = false) List<String> facets,
                                 @RequestParam(value = "filter", required = false) List<String> filters) {
        if (query == null || query.isBlank()) return new SearchResponse(List.of(), Map.of());
        return service.search(tenantId, query, normalizeFacetFields(facets), parseFilters(filters));
    }

    private List<String> normalizeFacetFields(List<String> facets) {
        if (facets == null || facets.isEmpty()) return List.of();
        return facets.stream()
                .map(String::trim)
                .filter(f -> !f.isBlank())
                .distinct()
                .sorted()
                .limit(MAX_FACET_FIELDS)
                .toList();
    }

    // Each filter is "field:value", e.g. filter=category:technology
    private Map<String, String> parseFilters(List<String> filters) {
        if (filters == null || filters.isEmpty()) return Map.of();
        Map<String, String> result = new TreeMap<>();
        for (String filter : filters) {
            int separator = filter.indexOf(':');
            if (separator <= 0 || separator == filter.length() - 1) continue;
            String field = filter.substring(0, separator).trim();
            String value = filter.substring(separator + 1).trim();
            if (!field.isBlank() && !value.isBlank()) result.put(field, value);
        }
        return result;
    }
}
