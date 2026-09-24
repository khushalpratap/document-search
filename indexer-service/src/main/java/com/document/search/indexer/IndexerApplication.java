package com.document.search.indexer;

import com.document.search.common.ElasticsearchIndexInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ElasticsearchIndexInitializer.class)
public class IndexerApplication {
    public static void main(String[] args) {
        SpringApplication.run(IndexerApplication.class, args);
    }
}
