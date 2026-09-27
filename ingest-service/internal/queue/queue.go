package queue

import (
	"context"
	"log"
	"sync"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/khushalpratap/document-search/ingest-service/internal/model"
)

// Queue decouples the HTTP handler from the database write: a request enqueues
// a document and returns immediately, while a fixed pool of worker goroutines
// drains the channel and performs the actual (blocking) Postgres insert
// concurrently. pgxpool.Pool is safe for concurrent use, so many workers can
// share the same pool without any extra locking on the caller's side.
type Queue struct {
	pool       *pgxpool.Pool
	jobs       chan model.Document
	wg         sync.WaitGroup
	maxRetries int
}

func New(pool *pgxpool.Pool, queueSize int) *Queue {
	return &Queue{
		pool:       pool,
		jobs:       make(chan model.Document, queueSize),
		maxRetries: 3,
	}
}

// Enqueue does a non-blocking send. It returns false when the queue is full so
// the caller can push back on the client (e.g. HTTP 503) instead of blocking
// the request goroutine or silently dropping the upload.
func (q *Queue) Enqueue(doc model.Document) bool {
	select {
	case q.jobs <- doc:
		return true
	default:
		return false
	}
}

// Start launches n worker goroutines that persist queued documents to
// Postgres until the queue is closed and drained.
func (q *Queue) Start(ctx context.Context, workerCount int) {
	for i := 0; i < workerCount; i++ {
		q.wg.Add(1)
		go q.worker(ctx, i)
	}
}

func (q *Queue) worker(ctx context.Context, id int) {
	defer q.wg.Done()
	for doc := range q.jobs {
		q.persist(ctx, doc)
	}
	log.Printf("worker %d: exiting, queue drained", id)
}

func (q *Queue) persist(ctx context.Context, doc model.Document) {
	var err error
	for attempt := 1; attempt <= q.maxRetries; attempt++ {
		err = q.insert(ctx, doc)
		if err == nil {
			return
		}
		log.Printf("document %s: insert attempt %d/%d failed: %v", doc.ID, attempt, q.maxRetries, err)
		time.Sleep(time.Duration(attempt) * 200 * time.Millisecond)
	}

	log.Printf("document %s: giving up after %d attempts: %v", doc.ID, q.maxRetries, err)
	q.markFailed(ctx, doc, err)
}

func (q *Queue) insert(ctx context.Context, doc model.Document) error {
	insertCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()

	_, err := q.pool.Exec(insertCtx, `
		INSERT INTO document_uploads
			(id, tenant_id, filename, content_type, size_bytes, checksum, metadata, status, created_at, processed_at)
		VALUES
			($1, $2, $3, $4, $5, $6, $7, $8, $9, now())
	`, doc.ID, doc.TenantID, doc.Filename, doc.ContentType, doc.SizeBytes, doc.Checksum, doc.Metadata, model.StatusCompleted, doc.CreatedAt)
	return err
}

// markFailed makes a best-effort record of the failure so the upload isn't
// silently lost; if even this write fails there is nothing left to do but log.
func (q *Queue) markFailed(ctx context.Context, doc model.Document, cause error) {
	failCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()

	_, err := q.pool.Exec(failCtx, `
		INSERT INTO document_uploads
			(id, tenant_id, filename, content_type, size_bytes, checksum, metadata, status, error, created_at, processed_at)
		VALUES
			($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, now())
		ON CONFLICT (id) DO UPDATE SET status = $8, error = $9, processed_at = now()
	`, doc.ID, doc.TenantID, doc.Filename, doc.ContentType, doc.SizeBytes, doc.Checksum, doc.Metadata, model.StatusFailed, cause.Error(), doc.CreatedAt)
	if err != nil {
		log.Printf("document %s: failed to record failure state: %v", doc.ID, err)
	}
}

// Shutdown closes the queue so no new jobs are accepted, then blocks until
// every already-queued document has been written and all workers have exited.
func (q *Queue) Shutdown(timeout time.Duration) {
	close(q.jobs)

	done := make(chan struct{})
	go func() {
		q.wg.Wait()
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(timeout):
		log.Printf("shutdown timed out after %s waiting on in-flight writes", timeout)
	}
}
