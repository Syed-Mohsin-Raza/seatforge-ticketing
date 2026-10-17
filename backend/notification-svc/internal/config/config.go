package config

import (
	"fmt"
	"os"
	"strconv"
)

type Config struct {
	QueueURL        string
	AWSRegion       string
	AWSEndpoint     string
	AWSAccessKey    string
	AWSSecretKey    string
	WorkerPoolSize  int
	WaitTimeSeconds int32
	VisibilitySecs  int32
}

func Load() (Config, error) {
	queueURL := getEnv("SEATFORGE_QUEUE_URL", "")
	if queueURL == "" {
		return Config{}, fmt.Errorf("SEATFORGE_QUEUE_URL is required")
	}

	workers, err := strconv.Atoi(getEnv("SEATFORGE_WORKER_POOL_SIZE", "5"))
	if err != nil || workers < 1 {
		return Config{}, fmt.Errorf("invalid SEATFORGE_WORKER_POOL_SIZE: %s", err)
	}

	waitTime, err := strconv.Atoi(getEnv("SEATFORGE_WAIT_TIME_SECONDS", "20"))
	if err != nil || waitTime < 0 || waitTime > 20 {
		return Config{}, fmt.Errorf("invalid SEATFORGE_WAIT_TIME_SECONDS: %s", err)
	}

	visibility, err := strconv.Atoi(getEnv("SEATFORGE_VISIBILITY_TIMEOUT_SECONDS", "30"))
	if err != nil || visibility < 0 {
		return Config{}, fmt.Errorf("invalid SEATFORGE_VISIBILITY_TIMEOUT_SECONDS: %s", err)
	}

	return Config{
		QueueURL:        queueURL,
		AWSRegion:       getEnv("AWS_REGION", "us-east-1"),
		AWSEndpoint:     getEnv("AWS_ENDPOINT_URL", ""),
		AWSAccessKey:    getEnv("AWS_ACCESS_KEY_ID", "test"),
		AWSSecretKey:    getEnv("AWS_SECRET_ACCESS_KEY", "test"),
		WorkerPoolSize:  workers,
		WaitTimeSeconds: int32(waitTime),
		VisibilitySecs:  int32(visibility),
	}, nil
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}