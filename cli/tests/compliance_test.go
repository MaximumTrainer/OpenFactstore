package tests

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

func TestAssert(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/assert" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		var req api.AssertRequest
		json.NewDecoder(r.Body).Decode(&req)
		if req.Sha256Digest == "" || req.FlowID == "" {
			t.Error("missing sha256 or flowId in request")
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.AssertResponse{
			Sha256Digest: req.Sha256Digest,
			FlowID:       req.FlowID,
			Status:       "COMPLIANT",
		})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	result, err := api.Assert(c, api.AssertRequest{
		Sha256Digest: "sha256:abc",
		FlowID:       "flow-1",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Status != "COMPLIANT" {
		t.Errorf("expected COMPLIANT, got %s", result.Status)
	}
}

func TestGetChainOfCustody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/compliance/artifact/sha256:abc" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.ChainOfCustodyResponse{
			Sha256Digest: "sha256:abc",
			Artifact:     &api.ArtifactResponse{ID: "art-1"},
			Trail:        &api.TrailResponse{ID: "trail-1"},
			Flow:         &api.FlowResponse{ID: "flow-1"},
		})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	coc, err := api.GetChainOfCustody(c, "sha256:abc")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if coc.Sha256Digest != "sha256:abc" {
		t.Errorf("unexpected digest: %s", coc.Sha256Digest)
	}
	if coc.Artifact == nil || coc.Artifact.ID != "art-1" {
		t.Error("unexpected artifact in chain of custody")
	}
}

func TestAssertNonCompliant(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.AssertResponse{
			Sha256Digest:            "sha256:def",
			FlowID:                  "flow-1",
			Status:                  "NON_COMPLIANT",
			MissingAttestationTypes: []string{"SBOM", "VULNERABILITY_SCAN"},
		})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	result, err := api.Assert(c, api.AssertRequest{Sha256Digest: "sha256:def", FlowID: "flow-1"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Status != "NON_COMPLIANT" {
		t.Errorf("expected NON_COMPLIANT, got %s", result.Status)
	}
	if len(result.MissingAttestationTypes) != 2 {
		t.Errorf("expected 2 missing types, got %d", len(result.MissingAttestationTypes))
	}
}
