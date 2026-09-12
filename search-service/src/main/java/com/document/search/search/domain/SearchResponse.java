package com.document.search.search.domain;

import java.util.List;
import java.util.Map;

public record SearchResponse(
        List<SearchResult> results,
        Map<String, List<FacetValue>> facets
) {
}