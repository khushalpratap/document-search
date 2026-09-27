package api

import (
	"context"
	"encoding/json"
	"net/http"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/khushalpratap/document-search/ingest-service/internal/model"
	"github.com/khushalpratap/document-search/ingest-service/internal/queue"
)

type Server struct {
	pool  *pgxpool.Pool
	queue *queue.Queue
}

func NewServer(pool *pgxpool.Pool, q *queue.Queue) *Server {
	return &Server{pool: pool, queue: q}
}

func (s *Server) Routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", s.handleHealth)
	mux.HandleFunc("POST /documents", s.handleUpload)
	mux.HandleFunc("GET /documents/{id}", s.handleGet)
	return mux
}

// handleUpload is the async ingestion endpoint: it validates the request,
// hands the document to the queue, and responds immediately without waiting
// for the Postgres write to happen.
func (s *Server) handleUpload(w http.ResponseWriter, r *http.Request) {
	var req model.UploadRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if err := req.Validate(); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	doc := model.NewDocument(req)

	if !s.queue.Enqueue(doc) {
		writeError(w, http.StatusServiceUnavailable, "ingestion queue is full, retry shortly")
		return
	}

	writeJSON(w, http.StatusAccepted, map[string]any{
		"id":        doc.ID,
		"status":    doc.Status,
		"queued_at": doc.CreatedAt,
		"tenant_id": doc.TenantID,
		"filename":  doc.Filename,
	})
}

// handleGet lets a client poll for the outcome of an async write.
func (s *Server) handleGet(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")

	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()

	row := s.pool.QueryRow(ctx, `
		SELECT id, tenant_id, filename, content_type, size_bytes, checksum, metadata, status, error, created_at, processed_at
		FROM document_uploads
		WHERE id = $1
	`, id)

	var doc model.Document
	err := row.Scan(&doc.ID, &doc.TenantID, &doc.Filename, &doc.ContentType, &doc.SizeBytes,
		&doc.Checksum, &doc.Metadata, &doc.Status, &doc.Error, &doc.CreatedAt, &doc.ProcessedAt)
	if err == pgx.ErrNoRows {
		writeError(w, http.StatusNotFound, "document not found (it may still be queued)")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to read document")
		return
	}

	writeJSON(w, http.StatusOK, doc)
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()

	if err := s.pool.Ping(ctx); err != nil {
		writeError(w, http.StatusServiceUnavailable, "database unreachable")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
