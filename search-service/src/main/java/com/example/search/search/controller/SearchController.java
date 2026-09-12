package com.example.search.search.controller;

import com.example.search.common.TenantHeaders;
import com.example.search.search.domain.SearchResult;
import com.example.search.search.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {
    private final SearchService service;
    public SearchController(SearchService service) { this.service = service; }

    @GetMapping
    public List<SearchResult> search(@RequestHeader(TenantHeaders.TENANT_ID) String tenantId,
                                     @RequestParam("q") String query) {
        if (query == null || query.isBlank()) return List.of();
        return service.search(tenantId, query);
    }
}
