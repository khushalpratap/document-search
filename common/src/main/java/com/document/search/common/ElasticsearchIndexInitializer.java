package com.document.search.common;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

/**
 * Creates the documents index on startup, and re-applies the mapping to an existing
 * index so newly added dynamic templates take effect. Services opt in with @Import.
 */
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private final ElasticsearchOperations operations;

    public ElasticsearchIndexInitializer(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations indexOperations = operations.indexOps(IndexedDocument.class);

        if (!indexOperations.exists()) {
            indexOperations.create();
        }
        indexOperations.putMapping(indexOperations.createMapping(IndexedDocument.class));
    }
}
