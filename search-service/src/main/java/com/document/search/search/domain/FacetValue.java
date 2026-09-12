package com.document.search.search.domain;

public record FacetValue(
        String value,
        long count
) {
}