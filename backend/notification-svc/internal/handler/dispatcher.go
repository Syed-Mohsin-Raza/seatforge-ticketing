package handler

import (
	"context"
	"fmt"
)

// Handler processes a single event. Returning a non-nil error causes
// the message to be redelivered (up to the DLQ threshold).
type Handler func(ctx context.Context, body string) error

type Dispatcher struct {
	handlers map[string]Handler
}

func NewDispatcher() *Dispatcher {
	return &Dispatcher{handlers: make(map[string]Handler)}
}

func (d *Dispatcher) Register(eventType string, h Handler) {
	d.handlers[eventType] = h
}

// Dispatch parses the envelope and routes to the registered handler.
// Unknown event types are logged and acknowledged (no error), because
// retrying an unsupported event would never succeed.
func (d *Dispatcher) Dispatch(ctx context.Context, eventType, body string) error {
	if eventType == "" {
		return fmt.Errorf("dispatch: empty eventType")
	}

	h, ok := d.handlers[eventType]
	if !ok {
		return nil // unknown event type: acknowledge silently
	}

	return h(ctx, body)
}
