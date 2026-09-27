// Command ingest-service accepts document upload metadata over HTTP and
// persists it to Postgres asynchronously: each request is captured and
// acknowledged immediately, while a pool of worker goroutines does the actual
// (blocking) database write concurrently in the background.
package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/khushalpratap/document-search/ingest-service/internal/api"
	"github.com/khushalpratap/document-search/ingest-service/internal/config"
	"github.com/khushalpratap/document-search/ingest-service/internal/db"
	"github.com/khushalpratap/document-search/ingest-service/internal/queue"
)

func main() {
	cfg := config.Load()
	log.Printf("starting ingest-service: %s", cfg)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	pool, err := db.NewPool(ctx, cfg.DatabaseURL, cfg.DBMaxConns)
	if err != nil {
		log.Fatalf("connect to database: %v", err)
	}
	defer pool.Close()

	if err := db.Migrate(ctx, pool); err != nil {
		log.Fatalf("apply schema: %v", err)
	}

	q := queue.New(pool, cfg.QueueSize)
	q.Start(ctx, cfg.WorkerCount)

	srv := &http.Server{
		Addr:    ":" + cfg.Port,
		Handler: api.NewServer(pool, q).Routes(),
	}

	go func() {
		log.Printf("listening on :%s", cfg.Port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("http server error: %v", err)
		}
	}()

	<-ctx.Done()
	log.Println("shutdown signal received, draining in-flight requests and writes")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), cfg.ShutdownTimeout)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Printf("http server shutdown error: %v", err)
	}

	// Stop accepting new jobs and wait for queued writes to finish before the
	// pool closes.
	q.Shutdown(cfg.ShutdownTimeout)

	log.Println("shutdown complete")
}
