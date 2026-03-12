package tests

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

func TestListTrails(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/trails" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode([]api.TrailResponse{
			{ID: "trail-1", FlowID: "flow-1", GitBranch: "main", Status: "PENDING"},
		})
	}))
	defer server.Close()

	c := client.New(server.URL, "tok")
	trails, err := api.ListTrails(c, "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(trails) != 1 || trails[0].ID != "trail-1" {
		t.Errorf("unexpected trails: %+v", trails)
	}
}

func TestListTrailsWithFlowID(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.RawQuery != "flowId=flow-1" {
			t.Errorf("expected flowId query param, got: %s", r.URL.RawQuery)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode([]api.TrailResponse{{ID: "trail-1", FlowID: "flow-1"}})
	}))
	defer server.Close()

	c := client.New(server.URL, "tok")
	trails, err := api.ListTrails(c, "flow-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(trails) != 1 {
		t.Errorf("expected 1 trail, got %d", len(trails))
	}
}

func TestGetTrail(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/trails/trail-1" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.TrailResponse{ID: "trail-1", FlowID: "flow-1"})
	}))
	defer server.Close()

	c := client.New(server.URL, "tok")
	trail, err := api.GetTrail(c, "trail-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if trail.ID != "trail-1" {
		t.Errorf("expected trail-1, got %s", trail.ID)
	}
}

func TestGetTrailNotFound(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(map[string]string{"message": "trail not found"})
	}))
	defer server.Close()

	c := client.New(server.URL, "tok")
	_, err := api.GetTrail(c, "missing")
	if err == nil {
		t.Fatal("expected error for 404")
	}
}

func TestCreateTrail(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/trails" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		var req api.CreateTrailRequest
		json.NewDecoder(r.Body).Decode(&req)
		if req.FlowID != "flow-1" {
			t.Errorf("expected FlowID flow-1, got %s", req.FlowID)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(api.TrailResponse{ID: "trail-new", FlowID: req.FlowID})
	}))
	defer server.Close()

	c := client.New(server.URL, "tok")
	trail, err := api.CreateTrail(c, api.CreateTrailRequest{
		FlowID:         "flow-1",
		GitCommitSha:   "abc123",
		GitBranch:      "main",
		GitAuthor:      "Alice",
		GitAuthorEmail: "alice@example.com",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if trail.ID != "trail-new" {
		t.Errorf("expected trail-new, got %s", trail.ID)
	}
}
