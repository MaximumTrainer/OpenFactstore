package tests

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

func TestListAttestations(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/trails/trail-1/attestations" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode([]api.AttestationResponse{
			{ID: "att-1", TrailID: "trail-1", Type: "SBOM", Status: "PASSED"},
		})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	attestations, err := api.ListAttestations(c, "trail-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(attestations) != 1 || attestations[0].ID != "att-1" {
		t.Errorf("unexpected result: %+v", attestations)
	}
}

func TestCreateAttestation(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/trails/trail-1/attestations" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		var req api.CreateAttestationRequest
		json.NewDecoder(r.Body).Decode(&req)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(api.AttestationResponse{
			ID:      "att-new",
			TrailID: "trail-1",
			Type:    req.Type,
			Status:  req.Status,
		})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	att, err := api.CreateAttestation(c, "trail-1", api.CreateAttestationRequest{
		Type:   "SBOM",
		Status: "PASSED",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if att.Type != "SBOM" || att.Status != "PASSED" {
		t.Errorf("unexpected attestation: %+v", att)
	}
}

func TestListAttestationsNotFound(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(map[string]string{"message": "trail not found"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	_, err := api.ListAttestations(c, "missing")
	if err == nil {
		t.Fatal("expected error for 404")
	}
}
