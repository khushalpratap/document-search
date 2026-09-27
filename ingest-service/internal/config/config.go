package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

// Config holds all runtime settings, sourced from environment variables so the
// service can be reconfigured per-deployment without a rebuild.
type Config struct {
	Port            string
	DatabaseURL     string
	WorkerCount     int
	QueueSize       int
	DBMaxConns      int32
	ShutdownTimeout time.Duration
}

func Load() Config {
	return Config{
		Port:            getEnv("PORT", "8080"),
		DatabaseURL:     getEnv("DATABASE_URL", "postgres://document:document@localhost:5432/documentdb?sslmode=disable"),
		WorkerCount:     getEnvInt("WORKER_COUNT", 8),
		QueueSize:       getEnvInt("QUEUE_SIZE", 1000),
		DBMaxConns:      int32(getEnvInt("DB_MAX_CONNS", 10)),
		ShutdownTimeout: getEnvDuration("SHUTDOWN_TIMEOUT", 15*time.Second),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	v := os.Getenv(key)
	if v == "" {
		return fallback
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return fallback
	}
	return n
}

func getEnvDuration(key string, fallback time.Duration) time.Duration {
	v := os.Getenv(key)
	if v == "" {
		return fallback
	}
	d, err := time.ParseDuration(v)
	if err != nil {
		return fallback
	}
	return d
}

func (c Config) String() string {
	return fmt.Sprintf("port=%s workers=%d queue_size=%d db_max_conns=%d", c.Port, c.WorkerCount, c.QueueSize, c.DBMaxConns)
}
