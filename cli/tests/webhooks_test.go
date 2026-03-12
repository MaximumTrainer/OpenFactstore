package tests

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

func TestListWebhooks(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/webhook-configs" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode([]api.WebhookConfigResponse{
			{ID: "wh-1", Source: "github", FlowID: "flow-1", IsActive: true},
		})
	}))
	defer server.Close()

	c := client.New(server.URL, "tok")
	webhooks, err := api.ListWebhooks(c)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(webhooks) != 1 || webhooks[0].ID != "wh-1" {
		t.Errorf("unexpected result: %+v", webhooks)
	}
}

func TestCreateWebhook(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/webhook-configs" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		var req api.CreateWebhookRequest
		json.NewDecoder(r.Body).Decode(&req)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(api.WebhookConfigResponse{
			ID:     "wh-new",
			Source: req.Source,
			FlowID: req.FlowID,
		})
	}))
	defer server.Close()

	c := client.New(server.URL, "tok")
	wh, err := api.CreateWebhook(c, api.CreateWebhookRequest{
		Source: "github",
		Secret: "s3cr3t",
		FlowID: "flow-1",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if wh.Source != "github" {
		t.Errorf("expected source github, got %s", wh.Source)
	}
}

func TestDeleteWebhook(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodDelete || r.URL.Path != "/api/v1/webhook-configs/wh-1" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	c := client.New(server.URL, "tok")
	if err := api.DeleteWebhook(c, "wh-1"); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestDeleteWebhookNotFound(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(map[string]string{"message": "webhook not found"})
	}))
	defer server.Close()

	c := client.New(server.URL, "tok")
	err := api.DeleteWebhook(c, "missing")
	if err == nil {
		t.Fatal("expected error for 404")
	}
}

func TestListWebhookDeliveries(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/webhook-configs/wh-1/deliveries" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode([]api.WebhookDeliveryResponse{
			{ID: "del-1", WebhookConfigID: "wh-1", Status: "PROCESSED", Source: "github"},
		})
	}))
	defer server.Close()

	c := client.New(server.URL, "tok")
	deliveries, err := api.ListWebhookDeliveries(c, "wh-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(deliveries) != 1 || deliveries[0].ID != "del-1" {
		t.Errorf("unexpected result: %+v", deliveries)
	}
}
