package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	awssqs "github.com/aws/aws-sdk-go-v2/service/sqs"

	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/notification-svc/internal/config"
	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/notification-svc/internal/handler"
	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/notification-svc/internal/sqs"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	slog.SetDefault(logger)

	cfg, err := config.Load()
	if err != nil {
		logger.Error("config load failed", "err", err)
		os.Exit(1)
	}

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	sqsClient, err := buildSQSClient(ctx, cfg)
	if err != nil {
		logger.Error("sqs client init failed", "err", err)
		os.Exit(1)
	}

	dispatcher := handler.NewDispatcher()
	for eventType, h := range handler.NewBookingHandlers(logger) {
		dispatcher.Register(eventType, h)
	}

	consumer := sqs.NewConsumer(sqsClient, dispatcher, cfg, logger)

	logger.Info("notification-svc starting",
		"queue", cfg.QueueURL,
		"workers", cfg.WorkerPoolSize,
		"region", cfg.AWSRegion,
		"endpoint", cfg.AWSEndpoint,
	)

	if err := consumer.Run(ctx); err != nil {
		logger.Error("consumer stopped with error", "err", err)
		os.Exit(1)
	}
	logger.Info("notification-svc shut down cleanly")
}

func buildSQSClient(ctx context.Context, cfg config.Config) (*awssqs.Client, error) {
	awsCfg, err := awsconfig.LoadDefaultConfig(ctx,
		awsconfig.WithRegion(cfg.AWSRegion),
		awsconfig.WithCredentialsProvider(
			credentials.NewStaticCredentialsProvider(cfg.AWSAccessKey, cfg.AWSSecretKey, ""),
		),
	)
	if err != nil {
		return nil, err
	}

	client := awssqs.NewFromConfig(awsCfg, func(o *awssqs.Options) {
		if cfg.AWSEndpoint != "" {
			o.BaseEndpoint = &cfg.AWSEndpoint
		}
	})
	return client, nil
}