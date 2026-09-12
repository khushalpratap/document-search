# search-service Performance Benchmark (k6)

Load-tests `GET /search` against the concurrency ramp and latency targets documented in the main [README.md, §15 Performance Strategy](../README.md#15-performance-strategy): **100 → 250 → 500 → 1000+ concurrent users**, p50/p95/p99 latency, and error rate, measured separately at each concurrency level.

## Install k6

```bash
brew install k6   # macOS
```

See [k6.io/docs](https://k6.io/docs/get-started/installation/) for other platforms.

## Run it

Bring the stack up first (`docker compose up -d` or run the modules locally — see main README §4), then:

```bash
k6 run k6/search-benchmark.js
```

By default this:

1. Seeds 40 sample documents per tenant (3 tenants) through `gateway` → `document-service`, so searches have real matches instead of hitting an empty index.
2. Runs four sequential stages against `search-service` directly (`http://localhost:8083`, i.e. through the blue-green nginx proxy — see main README §6): 100, 250, 500, then 1000 concurrent virtual users, each ramping up, holding, then ramping down.
3. Checks every response is `200` with a `results` array, tracks a custom `results_found_rate` metric (fraction of successful searches that actually matched something — a low value usually means the query pool doesn't match the seeded data, not a service problem), and enforces `p(95) < 500ms` per concurrency level plus an overall `< 1%` error-rate threshold.

## Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `BASE_URL` | `http://localhost:8083` | search-service target. Point at `:8091`/`:8092` to benchmark one blue/green color directly, or `:8080` to go through the gateway (adds its own rate limiting — see main README §11). |
| `GATEWAY_URL` | `http://localhost:8080` | Used only for seeding documents. |
| `API_KEY` | `assessment-key` | Gateway API key, used for seeding (and sent on search requests too, harmlessly ignored by search-service). |
| `TENANTS` | `tenant-001,tenant-002,tenant-003` | Comma-separated tenant IDs to spread traffic and seed data across. |
| `SEED` | `true` | Set `false` to skip seeding, e.g. if the index is already populated or you're re-running against the same data. |
| `DOCS_PER_TENANT` | `40` | Documents seeded per tenant when `SEED=true`. |
| `RAMP_UP` / `HOLD` / `RAMP_DOWN` | `15s` / `30s` / `10s` | Per-stage timing; total run time is `4 × (RAMP_UP + HOLD + RAMP_DOWN)` since the four concurrency levels run one after another. |

Example — benchmark `search-service-green` directly, skip reseeding, hold each level for a full minute:

```bash
BASE_URL=http://localhost:8092 SEED=false HOLD=60s k6 run k6/search-benchmark.js
```

## Reading the output

k6 prints per-level latency and a threshold pass/fail summary, e.g.:

```
http_req_duration{level:100}
✓ 'p(95)<500' p(95)=81ms

http_req_duration{level:1000}
✗ 'p(95)<500' p(95)=1.23s
```

If you see connection-level failures (`EOF`, `connection refused`) climbing sharply at the higher concurrency levels rather than gradually increasing latency, that's usually a sign you're hitting host/infrastructure limits — nginx `worker_connections`, the JVM's embedded Tomcat `max-threads`, Elasticsearch's search thread pool, or the OS file-descriptor limit (`ulimit -n`) — rather than a query-processing bottleneck in `SearchService` itself. Tune those before drawing conclusions about `search-service`'s own capacity, and re-run with `HOLD` long enough to get a stable read at each level.
