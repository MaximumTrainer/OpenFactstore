package tests

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

func TestListFlows(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet || r.URL.Path != "/api/v1/flows" {
			t.Errorf("unexpected request: %s %s", r.Method, r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode([]api.FlowResponse{
			{ID: "flow-1", Name: "My Flow", RequiredAttestationTypes: []string{"SBOM"}},
		})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	flows, err := api.ListFlows(c)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(flows) != 1 {
		t.Fatalf("expected 1 flow, got %d", len(flows))
	}
	if flows[0].ID != "flow-1" {
		t.Errorf("expected ID flow-1, got %s", flows[0].ID)
	}
}

func TestGetFlow(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/flows/flow-1" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.FlowResponse{ID: "flow-1", Name: "Test"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	flow, err := api.GetFlow(c, "flow-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if flow.ID != "flow-1" {
		t.Errorf("expected ID flow-1, got %s", flow.ID)
	}
}

func TestCreateFlow(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/flows" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		var req api.CreateFlowRequest
		json.NewDecoder(r.Body).Decode(&req)
		if req.Name != "New Flow" {
			t.Errorf("expected name 'New Flow', got '%s'", req.Name)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(api.FlowResponse{ID: "flow-new", Name: req.Name})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	flow, err := api.CreateFlow(c, api.CreateFlowRequest{Name: "New Flow", RequiredAttestationTypes: []string{"SBOM"}})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if flow.Name != "New Flow" {
		t.Errorf("expected name 'New Flow', got '%s'", flow.Name)
	}
}

func TestUpdateFlow(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPut || r.URL.Path != "/api/v1/flows/flow-1" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.FlowResponse{ID: "flow-1", Name: "Updated"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	flow, err := api.UpdateFlow(c, "flow-1", api.UpdateFlowRequest{Name: "Updated"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if flow.Name != "Updated" {
		t.Errorf("expected name 'Updated', got '%s'", flow.Name)
	}
}

func TestDeleteFlow(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodDelete || r.URL.Path != "/api/v1/flows/flow-1" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	if err := api.DeleteFlow(c, "flow-1"); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestGetFlowNotFound(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet || r.URL.Path != "/api/v1/flows/missing" {
			t.Errorf("unexpected request: %s %s", r.Method, r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(map[string]string{"message": "not found"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	_, err := api.GetFlow(c, "missing")
	if err == nil {
		t.Fatal("expected error for 404")
	}
}
