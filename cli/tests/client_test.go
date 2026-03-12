package tests

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

func TestClientGet(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			t.Errorf("expected GET, got %s", r.Method)
		}
		if r.URL.Path != "/api/v1/flows" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.Header.Get("Authorization") != "Bearer test-token" {
			t.Errorf("missing or incorrect Authorization header")
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode([]map[string]string{{"id": "1"}})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "test-token")
	body, status, err := c.Get("/api/v1/flows")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if status != http.StatusOK {
		t.Errorf("expected 200, got %d", status)
	}
	if len(body) == 0 {
		t.Error("expected non-empty body")
	}
}

func TestClientPost(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.Header.Get("Content-Type") != "application/json" {
			t.Errorf("expected application/json Content-Type")
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(map[string]string{"id": "new-id"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "test-token")
	body, status, err := c.Post("/api/v1/flows", map[string]string{"name": "test"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if status != http.StatusCreated {
		t.Errorf("expected 201, got %d", status)
	}
	if len(body) == 0 {
		t.Error("expected non-empty body")
	}
}

func TestClientPut(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPut {
			t.Errorf("expected PUT, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"id": "1"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "test-token")
	body, status, err := c.Put("/api/v1/flows/1", map[string]string{"name": "updated"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if status != http.StatusOK {
		t.Errorf("expected 200, got %d", status)
	}
	if len(body) == 0 {
		t.Error("expected non-empty body")
	}
}

func TestClientDelete(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodDelete {
			t.Errorf("expected DELETE, got %s", r.Method)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "test-token")
	_, status, err := c.Delete("/api/v1/flows/1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if status != http.StatusNoContent {
		t.Errorf("expected 204, got %d", status)
	}
}

func TestParseError(t *testing.T) {
	err := client.ParseError(404, []byte(`{"message":"not found"}`))
	if err == nil {
		t.Fatal("expected non-nil error")
	}
	if err.Error() != "API error 404: not found" {
		t.Errorf("unexpected error message: %s", err.Error())
	}
}

func TestParseErrorFallback(t *testing.T) {
	err := client.ParseError(500, []byte(`internal server error`))
	if err == nil {
		t.Fatal("expected non-nil error")
	}
	if err.Error() != "API error 500: internal server error" {
		t.Errorf("unexpected error message: %s", err.Error())
	}
}

func TestNewClientRejectsNonLocalhostHTTP(t *testing.T) {
	_, err := client.New("http://api.example.com", "token")
	if err == nil {
		t.Fatal("expected error for non-localhost http:// host")
	}
}

func TestNewClientAllowsLocalhost(t *testing.T) {
	_, err := client.New("http://localhost:8080", "token")
	if err != nil {
		t.Fatalf("expected no error for http://localhost, got: %v", err)
	}
}

func TestNewClientAllows127(t *testing.T) {
	_, err := client.New("http://127.0.0.1:8080", "token")
	if err != nil {
		t.Fatalf("expected no error for http://127.0.0.1, got: %v", err)
	}
}
