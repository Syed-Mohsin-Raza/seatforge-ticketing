FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN go build -o /out/notification-svc ./cmd/consumer

FROM alpine:3.20
RUN apk add --no-cache ca-certificates
COPY --from=build /out/notification-svc /usr/local/bin/notification-svc
ENTRYPOINT ["/usr/local/bin/notification-svc"]