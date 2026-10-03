Client → booking-api (Java, :8080) → gRPC → hold-svc (Go, :9090) → PostgreSQL
                                  ↘ Redis (cache)
