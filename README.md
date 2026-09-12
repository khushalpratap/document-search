# Distributed Document Search Service

Gradle multi-module Spring Boot prototype for a distributed, multi-tenant document search system.

## Modules

- `gateway`: authentication, tenant context, routing, Redis-backed rate limiting, correlation ID.
- `document-service`: document CRUD, PostgreSQL persistence, Kafka event publishing.
- `indexer-service`: Kafka consumer that builds the Elasticsearch search index.
- `search-service`: tenant-filtered full-text search with Caffeine query cache.
- `common`: small shared contracts only (Kafka event and headers).

## Architecture

```text
Client
  |
  v
Gateway :8080
  |-- /documents/** --> Document Service :8081 --> PostgreSQL
  |                                      |
  |                                      +--> Kafka document-events
  |
  +-- /search/** ----> Search Service :8083 --> Caffeine --> Elasticsearch
                                      ^
                                      |
                              Indexer Service :8082
                                      ^
                                      |
                                    Kafka
```

PostgreSQL is the source of truth. Elasticsearch is a derived, eventually consistent search index. Kafka decouples write traffic from indexing and allows indexers to scale independently.

## Run locally

Prerequisites: Java 21, Docker, Docker Compose, Gradle (or use a Gradle wrapper generated in your environment).

```bash
docker compose up -d postgres zookeeper kafka elasticsearch redis
```

For host-running Spring Boot processes, use the defaults in each `application.yml`:

```text
Gateway          8080
Document Service 8081
Indexer Service  8082
Search Service   8083
Postgres         5432
Kafka            9092
Elasticsearch    9200
Redis            6379
```

Start all modules from the IDE, or:

```bash
./gradlew :gateway:bootRun
./gradlew :document-service:bootRun
./gradlew :indexer-service:bootRun
./gradlew :search-service:bootRun
```

Or run everything with Docker:

```bash
docker compose up --build
```

## API

Gateway authentication for the prototype uses:

```text
X-API-Key: assessment-key
X-Tenant-Id: tenant-001
```

### Create document

```bash
curl -X POST http://localhost:8080/documents \
  -H 'X-API-Key: assessment-key' \
  -H 'X-Tenant-Id: tenant-001' \
  -H 'Content-Type: application/json' \
  -d '{
    "title":"Distributed Systems Architecture",
    "content":"Distributed systems use multiple independent services that communicate over a network. Kafka can be used for asynchronous event processing and Elasticsearch provides fast full-text search.",
    "metadata":{"category":"technology","author":"Khushal","department":"engineering"}
  }'
```

### Search

```bash
curl -G http://localhost:8080/search \
  -H 'X-API-Key: assessment-key' \
  -H 'X-Tenant-Id: tenant-001' \
  --data-urlencode 'q=distributed systems'
```

### Get document

```bash
curl http://localhost:8080/documents/<DOCUMENT_ID> \
  -H 'X-API-Key: assessment-key' \
  -H 'X-Tenant-Id: tenant-001'
```

### Delete document

```bash
curl -X DELETE http://localhost:8080/documents/<DOCUMENT_ID> \
  -H 'X-API-Key: assessment-key' \
  -H 'X-Tenant-Id: tenant-001'
```

### Health

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

## Multi-tenancy

`tenantId` is carried in `X-Tenant-Id`. The gateway requires it and all downstream APIs scope data by tenant. Elasticsearch maps `tenantId` as `keyword` and search uses a `term` filter, preventing analyzed text matching from weakening isolation.

## Production evolution

For a production implementation, replace the direct PostgreSQL + Kafka publish in `document-service` with a transactional outbox. Add JWT/OIDC authentication, service-to-service identity, Kafka replication, Elasticsearch multi-node shards/replicas, Redis cluster, autoscaling, circuit breakers, retries/DLQs, idempotent consumers, OpenTelemetry, metrics and SLO-based alerting.

The prototype intentionally keeps those concerns lightweight so the core distributed flow is easy to run and review.
