package tests

import (
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// mustNewClient creates a client for testing, failing the test on error.
// In tests, server.URL is always http://127.0.0.1:<port> which is allowed.
func mustNewClient(t *testing.T, baseURL, token string) *client.Client {
	t.Helper()
	c, err := client.New(baseURL, token)
	if err != nil {
		t.Fatalf("client.New(%q): %v", baseURL, err)
	}
	return c
}
