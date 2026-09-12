package com.example.search.document.domain;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record DocumentRequest(
        @NotBlank String title,
        @NotBlank String content,
        Map<String, Object> metadata
) {}
