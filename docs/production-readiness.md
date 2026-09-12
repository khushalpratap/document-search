# Production Readiness Analysis

## 100x scale

- Gateway: stateless replicas behind a load balancer.
- Search: stateless replicas; shard Elasticsearch by document corpus and scale data nodes/replicas.
- Kafka: increase partitions and broker capacity; scale indexer consumer replicas up to partition count.
- PostgreSQL: primary + read replicas where appropriate, partitioning/archival for very large datasets, connection pooling.
- Redis: Redis Cluster for distributed rate limiting and optional shared search cache.

## Resilience

- Transactional outbox for reliable DB-to-Kafka delivery.
- Kafka replication factor >= 3 and producer idempotence.
- Retry with backoff and DLQ for poison indexing events.
- Idempotent indexer operations keyed by document ID and event/version ordering.
- Elasticsearch replicas and shard allocation across failure domains.
- Gateway timeouts, circuit breakers and bulkheads.

## Security

- Replace prototype API key with OIDC/JWT.
- Derive tenant identity from trusted claims rather than accepting arbitrary client tenant headers.
- TLS everywhere, secrets in a secret manager, least-privilege service accounts, audit logs and encryption at rest.

## Observability

Track request rate, p50/p95/p99 latency, 4xx/5xx, gateway throttles, Kafka consumer lag, indexing failures, Elasticsearch latency, cache hit rate, PostgreSQL connection pool saturation and JVM metrics. Propagate correlation/trace IDs.

## 99.95% SLA

99.95% monthly availability allows about 21.9 minutes of unavailability in a 30-day month. Define separate SLOs for API availability and search freshness. Search freshness should have a measurable target such as a p95 event-to-index delay, rather than pretending asynchronous indexing is strongly consistent.
