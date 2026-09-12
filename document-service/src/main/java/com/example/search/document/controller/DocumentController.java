package com.example.search.document.controller;

import com.example.search.common.TenantHeaders;
import com.example.search.document.domain.*;
import com.example.search.document.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/documents")
public class DocumentController {
    private final DocumentService service;
    public DocumentController(DocumentService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@RequestHeader(TenantHeaders.TENANT_ID) String tenantId,
                                   @Valid @RequestBody DocumentRequest request) {
        return service.create(tenantId, request);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@RequestHeader(TenantHeaders.TENANT_ID) String tenantId,
                                @PathVariable String id) {
        return service.get(tenantId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(TenantHeaders.TENANT_ID) String tenantId,
                       @PathVariable String id) {
        service.delete(tenantId, id);
    }
}
