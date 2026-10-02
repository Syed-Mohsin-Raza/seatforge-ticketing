.PHONY: proto build test run tidy clean

PROTO_DIR := proto
PB_DIR := internal/pb

proto:
	mkdir -p $(PB_DIR)
	protoc \
		--proto_path=$(PROTO_DIR) \
		--go_out=$(PB_DIR) \
		--go_opt=paths=source_relative \
		--go-grpc_out=$(PB_DIR) \
		--go-grpc_opt=paths=source_relative \
		$(PROTO_DIR)/hold.proto

build: proto
	go build -o bin/hold-svc ./cmd/server

test: proto
	go test ./...

run: proto
	go run ./cmd/server

tidy:
	go mod tidy

clean:
	rm -rf bin $(PB_DIR)

race:
	go test -race ./...