CREATE TABLE IF NOT EXISTS document_uploads (
    id            UUID PRIMARY KEY,
    tenant_id     TEXT NOT NULL,
    filename      TEXT NOT NULL,
    content_type  TEXT NOT NULL,
    size_bytes    BIGINT NOT NULL,
    checksum      TEXT,
    metadata      JSONB NOT NULL DEFAULT '{}'::jsonb,
    status        TEXT NOT NULL DEFAULT 'queued',
    error         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_document_uploads_tenant_id ON document_uploads (tenant_id);
CREATE INDEX IF NOT EXISTS idx_document_uploads_status ON document_uploads (status);
