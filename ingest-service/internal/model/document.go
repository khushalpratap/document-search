package model

import (
	"encoding/json"
	"errors"
	"time"

	"github.com/google/uuid"
)

// UploadRequest is the client-facing payload describing a document to save.
// The actual file bytes are assumed to live elsewhere (object storage); this
// service only captures and persists the upload metadata.
type UploadRequest struct {
	TenantID    string          `json:"tenant_id"`
	Filename    string          `json:"filename"`
	ContentType string          `json:"content_type"`
	SizeBytes   int64           `json:"size_bytes"`
	Checksum    string          `json:"checksum,omitempty"`
	Metadata    json.RawMessage `json:"metadata,omitempty"`
}

func (r UploadRequest) Validate() error {
	switch {
	case r.TenantID == "":
		return errors.New("tenant_id is required")
	case r.Filename == "":
		return errors.New("filename is required")
	case r.ContentType == "":
		return errors.New("content_type is required")
	case r.SizeBytes < 0:
		return errors.New("size_bytes must not be negative")
	}
	return nil
}

// Document is a queued upload as it moves through the system: captured
// immediately on request, then written to Postgres by a background worker.
type Document struct {
	ID          uuid.UUID       `json:"id"`
	TenantID    string          `json:"tenant_id"`
	Filename    string          `json:"filename"`
	ContentType string          `json:"content_type"`
	SizeBytes   int64           `json:"size_bytes"`
	Checksum    *string         `json:"checksum,omitempty"`
	Metadata    json.RawMessage `json:"metadata,omitempty"`
	Status      string          `json:"status"`
	Error       *string         `json:"error,omitempty"`
	CreatedAt   time.Time       `json:"created_at"`
	ProcessedAt *time.Time      `json:"processed_at,omitempty"`
}

const (
	StatusQueued    = "queued"
	StatusCompleted = "completed"
	StatusFailed    = "failed"
)

func NewDocument(req UploadRequest) Document {
	metadata := req.Metadata
	if len(metadata) == 0 {
		metadata = json.RawMessage("{}")
	}
	var checksum *string
	if req.Checksum != "" {
		checksum = &req.Checksum
	}
	return Document{
		ID:          uuid.New(),
		TenantID:    req.TenantID,
		Filename:    req.Filename,
		ContentType: req.ContentType,
		SizeBytes:   req.SizeBytes,
		Checksum:    checksum,
		Metadata:    metadata,
		Status:      StatusQueued,
		CreatedAt:   time.Now().UTC(),
	}
}
