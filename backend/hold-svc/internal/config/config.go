package config

import (
	"fmt"
	"os"
	"strconv"
)

type Config struct {
	GRPCPort    int
	DatabaseURL string
}

func Load() (Config, error) {
	port, err := strconv.Atoi(getEnv("GRPC_PORT", "9090"))
	if err != nil {
		return Config{}, fmt.Errorf("invalid GRPC_PORT: %w", err)
	}

	dbURL := getEnv("DATABASE_URL",
		"postgres://seatforge:seatforge@localhost:5432/seatforge?sslmode=disable")

	return Config{
		GRPCPort:    port,
		DatabaseURL: dbURL,
	}, nil
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}