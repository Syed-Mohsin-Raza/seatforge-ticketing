package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/hold-svc/internal/config"
	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/hold-svc/internal/db"
	grpcserver "github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/hold-svc/internal/grpc"
	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/hold-svc/internal/hold"
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

	database, err := db.Open(ctx, cfg.DatabaseURL)
	if err != nil {
		logger.Error("db open failed", "err", err)
		os.Exit(1)
	}
	defer database.Close()
	logger.Info("database connected")

	holdService := hold.NewService(database)
	grpcSrv := grpcserver.NewServer(holdService, logger)

	server, listener, err := grpcserver.Listen(cfg.GRPCPort, grpcSrv, logger)
	if err != nil {
		logger.Error("grpc listen failed", "err", err)
		os.Exit(1)
	}

	go func() {
		<-ctx.Done()
		logger.Info("shutdown signal received")
		server.GracefulStop()
	}()

	if err := server.Serve(listener); err != nil {
		logger.Error("grpc serve failed", "err", err)
	}

	logger.Info("shutdown complete")
}