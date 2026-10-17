package sqs

import (
	"context"
	"log/slog"
	"sync"
	"time"

	awssqs "github.com/aws/aws-sdk-go-v2/service/sqs"
	"github.com/aws/aws-sdk-go-v2/service/sqs/types"

	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/notification-svc/internal/config"
	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/notification-svc/internal/handler"
)

type Consumer struct {
	client     *awssqs.Client
	dispatcher *handler.Dispatcher
	config     config.Config
	logger     *slog.Logger
}

func NewConsumer(client *awssqs.Client, dispatcher *handler.Dispatcher, cfg config.Config, logger *slog.Logger) *Consumer {
	return &Consumer{
		client:     client,
		dispatcher: dispatcher,
		config:     cfg,
		logger:     logger,
	}
}

// Run starts the worker pool. Blocks until ctx is cancelled.
func (c *Consumer) Run(ctx context.Context) error {
	var wg sync.WaitGroup
	for i := 0; i < c.config.WorkerPoolSize; i++ {
		wg.Add(1)
		go func(workerID int) {
			defer wg.Done()
			c.workerLoop(ctx, workerID)
		}(i)
	}
	wg.Wait()
	return nil
}

func (c *Consumer) workerLoop(ctx context.Context, workerID int) {
	logger := c.logger.With("worker", workerID)
	logger.Info("worker started")
	defer logger.Info("worker stopped")

	for {
		select {
		case <-ctx.Done():
			return
		default:
		}

		messages, err := c.receive(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			logger.Error("receive failed", "err", err)
			time.Sleep(time.Second)
			continue
		}

		for _, msg := range messages {
			c.processOne(ctx, logger, msg)
		}
	}
}

func (c *Consumer) receive(ctx context.Context) ([]types.Message, error) {
	out, err := c.client.ReceiveMessage(ctx, &awssqs.ReceiveMessageInput{
		QueueUrl:              &c.config.QueueURL,
		MaxNumberOfMessages:   10,
		WaitTimeSeconds:       c.config.WaitTimeSeconds,
		VisibilityTimeout:     c.config.VisibilitySecs,
		MessageAttributeNames: []string{"All"},
	})
	if err != nil {
		return nil, err
	}
	return out.Messages, nil
}

func (c *Consumer) processOne(ctx context.Context, logger *slog.Logger, msg types.Message) {
	body := ""
	if msg.Body != nil {
		body = *msg.Body
	}

	eventType := "unknown"
	if v, ok := msg.MessageAttributes["eventType"]; ok && v.StringValue != nil {
		eventType = *v.StringValue
	}

	eventLogger := logger.With("eventType", eventType)
	if msg.MessageId != nil {
		eventLogger = eventLogger.With("messageId", *msg.MessageId)
	}

	eventLogger.Debug("processing message")

	if err := c.dispatcher.Dispatch(ctx, eventType, body); err != nil {
		eventLogger.Error("dispatch failed", "err", err)
		return
	}

	if _, err := c.client.DeleteMessage(ctx, &awssqs.DeleteMessageInput{
		QueueUrl:      &c.config.QueueURL,
		ReceiptHandle: msg.ReceiptHandle,
	}); err != nil {
		eventLogger.Error("delete failed", "err", err)
		return
	}
	eventLogger.Info("message processed and deleted")
}
