package tests

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

func TestListArtifacts(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v2/trails/trail-1/artifacts" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode([]api.ArtifactResponse{
			{ID: "art-1", TrailID: "trail-1", ImageName: "myapp", ImageTag: "v1.0", Sha256Digest: "sha256:abc"},
		})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	artifacts, err := api.ListArtifacts(c, "trail-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(artifacts) != 1 || artifacts[0].ID != "art-1" {
		t.Errorf("unexpected result: %+v", artifacts)
	}
}

func TestFindArtifact(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v2/artifacts" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.URL.Query().Get("sha256") != "sha256:abc" {
			t.Errorf("unexpected sha256 query param: %s", r.URL.Query().Get("sha256"))
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.ArtifactResponse{ID: "art-1", Sha256Digest: "sha256:abc"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	artifact, err := api.FindArtifact(c, "sha256:abc")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if artifact.ID != "art-1" {
		t.Errorf("expected art-1, got %s", artifact.ID)
	}
}

func TestFindArtifactNotFound(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(map[string]string{"message": "artifact not found"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	_, err := api.FindArtifact(c, "sha256:notexist")
	if err == nil {
		t.Fatal("expected error for 404")
	}
}

func TestCreateArtifact(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v2/trails/trail-1/artifacts" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		var req api.CreateArtifactRequest
		json.NewDecoder(r.Body).Decode(&req)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(api.CommandResult{
			ID:     "art-new",
			Status: "created",
		})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	result, err := api.CreateArtifact(c, "trail-1", api.CreateArtifactRequest{
		ImageName:    "myapp",
		ImageTag:     "v2.0",
		Sha256Digest: "sha256:def",
		ReportedBy:   "ci-bot",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.ID != "art-new" {
		t.Errorf("expected art-new, got %s", result.ID)
	}
}
