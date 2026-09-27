# ============================================================
# Build stage
# ============================================================
FROM golang:1.24-alpine AS builder

WORKDIR /workspace

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 go build -o /out/ingest-service .

# ============================================================
# Runtime stage
# ============================================================
FROM alpine:3.20

RUN apk add --no-cache ca-certificates curl

COPY --from=builder /out/ingest-service /app/ingest-service

EXPOSE 8080

ENTRYPOINT ["/app/ingest-service"]
