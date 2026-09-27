# ingest-service

Minimal Go service for asynchronously capturing document upload metadata into
Postgres. It is intentionally decoupled from the rest of the (Java) stack in
this repo — a standalone example of accepting a write request immediately and
persisting it concurrently in the background.

## Behavior

1. `POST /documents` validates the request, assigns it an ID, and pushes it
   onto an in-memory job queue. It responds `202 Accepted` immediately,
   **before** anything is written to the database.
2. A fixed pool of worker goroutines (`WORKER_COUNT`) drains that queue and
   inserts each document into Postgres concurrently, sharing one
   `pgxpool.Pool` connection pool (safe for concurrent use across goroutines).
3. If the queue is full (the database can't keep up with the request rate),
   the handler returns `503` immediately instead of blocking the request or
   silently dropping the upload — the caller is expected to retry.
4. `GET /documents/{id}` lets a client poll for the outcome of the async
   write (`queued` briefly, then `completed` or `failed`).
5. `GET /healthz` pings the database.

On `SIGINT`/`SIGTERM` the HTTP server stops accepting new requests, the queue
is closed, and the process waits (up to `SHUTDOWN_TIMEOUT`) for in-flight
writes to finish before exiting, so a deploy/restart doesn't lose queued
uploads.

## Configuration (env vars)

| Variable          | Default                                                                   |
|-------------------|----------------------------------------------------------------------------|
| `PORT`             | `8080`                                                                     |
| `DATABASE_URL`     | `postgres://document:document@localhost:5432/documentdb?sslmode=disable`  |
| `WORKER_COUNT`     | `8`                                                                        |
| `QUEUE_SIZE`       | `1000`                                                                     |
| `DB_MAX_CONNS`     | `10`                                                                       |
| `SHUTDOWN_TIMEOUT` | `15s`                                                                      |

The `document_uploads` table is created automatically on startup (idempotent
DDL in `internal/db/schema.sql`, mirrored in `migrations/001_init.sql`).

## Run locally

```bash
go run .
```

## Example

```bash
curl -X POST localhost:8080/documents \
  -H 'Content-Type: application/json' \
  -d '{"tenant_id":"t1","filename":"a.pdf","content_type":"application/pdf","size_bytes":1024}'
# -> 202 {"id":"...","status":"queued",...}

curl localhost:8080/documents/<id>
# -> 200 {"id":"...","status":"completed",...}
```
