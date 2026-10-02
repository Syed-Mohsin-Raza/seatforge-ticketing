package grpc

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"time"

	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/hold-svc/internal/hold"
	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/hold-svc/internal/pb"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/reflection"
	"google.golang.org/grpc/status"
)

type Server struct {
	pb.UnimplementedHoldServiceServer
	service *hold.Service
	logger  *slog.Logger
}

func NewServer(service *hold.Service, logger *slog.Logger) *Server {
	return &Server{service: service, logger: logger}
}

func (s *Server) ReserveSeat(ctx context.Context, req *pb.ReserveSeatRequest) (*pb.ReserveSeatResponse, error) {
    ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
    defer cancel()
    resp, err := s.service.ReserveSeat(ctx, req)
    if err != nil {
        return nil, mapError(err)
    }
    return resp, nil
}

func (s *Server) ReleaseSeat(ctx context.Context, req *pb.ReleaseSeatRequest) (*pb.ReleaseSeatResponse, error) {
	ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
    defer cancel()
	resp, err := s.service.ReleaseSeat(ctx, req)
	if err != nil {
		return nil, mapError(err)
	}
	return resp, nil
}

func mapError(err error) error {
	switch {
	case errors.Is(err, hold.ErrSeatNotFound):
		return status.Error(codes.NotFound, err.Error())
	case errors.Is(err, hold.ErrSeatAlreadyHeld),
		errors.Is(err, hold.ErrSeatAlreadyBooked):
		return status.Error(codes.AlreadyExists, err.Error())
	default:
		return status.Error(codes.Internal, err.Error())
	}
}

func Listen(port int, server *Server, logger *slog.Logger) (*grpc.Server, net.Listener, error) {
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		return nil, nil, fmt.Errorf("listen: %w", err)
	}

	grpcServer := grpc.NewServer()
	pb.RegisterHoldServiceServer(grpcServer, server)
	reflection.Register(grpcServer)

	logger.Info("grpc server listening", "port", port)
	return grpcServer, lis, nil
}