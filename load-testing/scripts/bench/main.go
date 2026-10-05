// backend/hold-svc/cmd/bench/main.go
package main

import (
    "context"
    "fmt"
    "sync"
    "sync/atomic"
    "time"
    

    "github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/hold-svc/internal/pb"
    "google.golang.org/grpc"
    "google.golang.org/grpc/credentials/insecure"
)

func main() {
    conn, err := grpc.NewClient("localhost:9090",
        grpc.WithTransportCredentials(insecure.NewCredentials()))
    if err != nil {
        panic(err)
    }
    defer conn.Close()

    client := pb.NewHoldServiceClient(conn)
    const workers = 100
    const requests = 10000

    var success, fail, totalMs int64
    var wg sync.WaitGroup

    start := time.Now()
    ch := make(chan int, requests)
    for i := 0; i < requests; i++ {
        ch <- 90000 + i  // distinct seats in an unused range
    }
    close(ch)

    for w := 0; w < workers; w++ {
        wg.Add(1)
        go func() {
            defer wg.Done()
            for seatID := range ch {
                t0 := time.Now()
                _, err := client.ReserveSeat(context.Background(), &pb.ReserveSeatRequest{
                    SeatId:              int64(seatID),
                    UserId:              fmt.Sprintf("bench-%d", seatID),
                    HoldDurationSeconds: 60,
                })
                elapsed := time.Since(t0).Milliseconds()
                atomic.AddInt64(&totalMs, elapsed)
                if err != nil {
                    atomic.AddInt64(&fail, 1)
                    fmt.Printf("FAIL seat=%d err=%v\n", seatID, err)
                    continue
                } else {
                    atomic.AddInt64(&success, 1)
                }
            }
        }()
    }
    wg.Wait()
    total := time.Since(start)

    avgMs := float64(totalMs) / float64(requests)
    fmt.Printf("Requests: %d, Workers: %d\n", requests, workers)
    fmt.Printf("Success: %d, Fail: %d\n", success, fail)
    fmt.Printf("Total: %v\n", total)
    fmt.Printf("Avg per request: %.2f ms\n", avgMs)
    fmt.Printf("Throughput: %.0f req/s\n", float64(requests)/total.Seconds())
}