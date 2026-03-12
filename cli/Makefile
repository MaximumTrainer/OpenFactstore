.PHONY: build test clean lint

build:
	CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o bin/factstore ./cmd/factstore

test:
	go test ./...

clean:
	rm -rf bin/

lint:
	go vet ./...
