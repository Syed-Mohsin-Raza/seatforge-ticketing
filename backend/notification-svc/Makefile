.PHONY: build test run tidy clean race

build:
	go build -o bin/notification-svc ./cmd/consumer

test:
	go test ./...

race:
	go test -race ./...

run:
	go run ./cmd/consumer

tidy:
	go mod tidy

clean:
	rm -rf bin