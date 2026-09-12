# Distributed Document Search Service

Gradle multi-module Spring Boot prototype for a distributed, multi-tenant document search system.

## Modules

- `gateway`: authentication, tenant context, routing, Redis-backed rate limiting, correlation ID.
- `document-service`: document CRUD, PostgreSQL persistence, Kafka event publishing.
- `indexer-service`: Kafka consumer that builds the Elasticsearch search index.
- `search-service`: tenant-filtered full-text search with Caffeine query cache.
- `common`: small shared contracts only (Kafka event and headers).

PostgreSQL is the source of truth. Elasticsearch is a derived, eventually consistent search index. Kafka decouples write
traffic from indexing and allows indexers to scale independently.

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

`search-service` runs blue-green behind an nginx proxy (
see [Blue-Green Deployment](#blue-green-deployment-search-service) below); `gateway` still reaches it at
`http://search-service:8083` without any changes.

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

For `search-service` specifically, `8083` goes through the nginx proxy to whichever color is active; hit `8091`/`8092`
to check blue/green directly:

```bash
curl http://localhost:8091/actuator/health   # search-service-blue
curl http://localhost:8092/actuator/health   # search-service-green
```

## Blue-Green Deployment (search-service)

`search-service` runs as two identical containers, `search-service-blue` and `search-service-green`, fronted by an
`nginx` reverse proxy that owns the shared `search-service` network identity and port (`8083`). `gateway`'s
`SEARCH_SERVICE_URL` always points at the proxy, so switching colors never requires touching the gateway.

```text
Gateway --> search-service (nginx :8083) --> search-service-blue  (:8083, host 8091)
                                          \-> search-service-green (:8083, host 8092)
```

Only the color named in `nginx/search-service/conf.d/active.conf` receives traffic; the other color is idle and safe to
rebuild/redeploy.

Relevant files:

- `nginx/search-service/nginx.conf` — proxy config; the upstream reads its single member from `conf.d/active.conf`.
- `nginx/search-service/conf.d/active.conf` — the switchable file (one `server ...;` line naming the live color).
- `scripts/switch-search-color.sh` — flips the active color with zero downtime.

### Deploying a new search-service version

1. Build and start the **idle** color with the new code (example: green is idle):
   ```bash
   docker compose up -d --build search-service-green
   ```
2. Smoke test it directly, bypassing the proxy:
   ```bash
   curl -G http://localhost:8092/search -H 'X-API-Key: assessment-key' -H 'X-Tenant-Id: tenant-001' --data-urlencode 'q=distributed systems'
   ```
3. Cut traffic over:
   ```bash
   ./scripts/switch-search-color.sh green
   ```
   This checks `http://localhost:8092/actuator/health`, rewrites `active.conf`, then runs `nginx -t && nginx -s reload`
   inside the proxy container — no dropped requests, no gateway restart.
4. Roll back instantly if needed by switching back to the previous color:
   ```bash
   ./scripts/switch-search-color.sh blue
   ```
5. The next deployment targets whichever color is now idle, alternating each release.

Use `--force` to skip the health-check gate (e.g. testing the script itself):
`./scripts/switch-search-color.sh green --force`.

## Multi-tenancy

`tenantId` is carried in `X-Tenant-Id`. The gateway requires it and all downstream APIs scope data by tenant.
Elasticsearch maps `tenantId` as `keyword` and search uses a `term` filter, preventing analyzed text matching from
weakening isolation.

## Production evolution

For a production implementation, replace the direct PostgreSQL + Kafka publish in `document-service` with a
transactional outbox. Add JWT/OIDC authentication, service-to-service identity, Kafka replication, Elasticsearch
multi-node shards/replicas, Redis cluster, autoscaling, circuit breakers, retries/DLQs, idempotent consumers,
OpenTelemetry, metrics and SLO-based alerting.

The prototype intentionally keeps those concerns lightweight so the core distributed flow is easy to run and review.
