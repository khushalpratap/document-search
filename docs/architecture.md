# Architecture Notes

## Components

1. **API Gateway** - single public entry point; validates API key, requires tenant context, adds correlation ID, routes requests and applies Redis-backed rate limits.
2. **Document Service** - write API and source-of-truth persistence in PostgreSQL. Publishes document lifecycle events to Kafka.
3. **Indexer Service** - Kafka consumer group that materializes the Elasticsearch search index. Can be scaled independently by adding instances/partitions.
4. **Search Service** - read/search API. Uses tenant-filtered Elasticsearch full-text search and a bounded Caffeine cache.
5. **Kafka** - asynchronous decoupling between document writes and search indexing.
6. **Redis** - gateway rate limiting. Search cache is local Caffeine in this prototype; production can use distributed Redis caching when cross-instance cache sharing is required.

## Consistency

Document writes are strongly committed to PostgreSQL before the request returns. Search is eventually consistent because Elasticsearch is populated asynchronously from Kafka. A transactional outbox is the production recommendation to avoid the database/Kafka dual-write gap.

## Tenant isolation

The gateway requires `X-Tenant-Id`. Document APIs query PostgreSQL by `(documentId, tenantId)`. Elasticsearch maps `tenantId` as `keyword` and every search request applies a `term` filter on it.

## Performance

The expected hot path for search is Gateway -> Search Service -> Caffeine hit, or Gateway -> Search Service -> Elasticsearch. Elasticsearch should be benchmarked with representative query distributions before making a 500ms p95 claim. Horizontal scaling is achieved independently for gateway, search, document service, indexers, Kafka partitions and Elasticsearch nodes.
