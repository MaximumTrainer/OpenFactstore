package tests

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

// #250: `factstore attest junit|snyk|generic` records on /api/v2, the v1 twin being deprecated,
// and still returns the recorded attestation, read back from the v2 list.
func TestRecordTypedAttestationUsesV2(t *testing.T) {
	var posted api.TypedAttestRequest
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v2/trails/trail-1/attestations":
			json.NewDecoder(r.Body).Decode(&posted)
			w.WriteHeader(http.StatusCreated)
			json.NewEncoder(w).Encode(api.CommandResult{ID: "att-2", Status: "created"})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v2/trails/trail-1/attestations":
			json.NewEncoder(w).Encode([]api.AttestationResponse{
				{ID: "att-1", TrailID: "trail-1", Type: "snyk", Status: "PASSED"},
				{ID: "att-2", TrailID: "trail-1", Type: "junit", Status: "FAILED"},
			})
		default:
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	result, err := api.RecordTypedAttestation(c, "trail-1", api.TypedAttestRequest{
		Type: "junit", Status: "PASSED", AttestationData: `{"tests":1}`, GitBranch: "main",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.ID != "att-2" || result.Status != "FAILED" {
		t.Errorf("expected the recorded attestation as the server judged it, got %+v", result)
	}
	if posted.AttestationData != `{"tests":1}` || posted.GitBranch != "main" {
		t.Errorf("request fields not sent: %+v", posted)
	}
}
