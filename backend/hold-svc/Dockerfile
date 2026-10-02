FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN apk add --no-cache protobuf protobuf-dev
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@latest && \
    go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
ENV PATH="/root/go/bin:${PATH}"
RUN make build

FROM alpine:3.20
RUN apk add --no-cache ca-certificates
COPY --from=build /src/bin/hold-svc /usr/local/bin/hold-svc
ENTRYPOINT ["/usr/local/bin/hold-svc"]