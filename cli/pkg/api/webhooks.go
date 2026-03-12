package api

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// WebhookConfigResponse mirrors the backend WebhookConfigResponse DTO.
type WebhookConfigResponse struct {
	ID        string `json:"id"`
	Source    string `json:"source"`
	FlowID    string `json:"flowId"`
	IsActive  bool   `json:"isActive"`
	CreatedAt string `json:"createdAt"`
}

// WebhookDeliveryResponse mirrors the backend WebhookDeliveryResponse DTO.
type WebhookDeliveryResponse struct {
	ID              string `json:"id"`
	WebhookConfigID string `json:"webhookConfigId"`
	DeliveryID      string `json:"deliveryId"`
	Source          string `json:"source"`
	EventType       string `json:"eventType,omitempty"`
	Status          string `json:"status"`
	StatusMessage   string `json:"statusMessage,omitempty"`
	ReceivedAt      string `json:"receivedAt"`
}

// CreateWebhookRequest is the body for POST /api/v1/webhook-configs.
type CreateWebhookRequest struct {
	Source string `json:"source"`
	Secret string `json:"secret"`
	FlowID string `json:"flowId"`
}

// ListWebhooks returns all webhook configurations.
func ListWebhooks(c *client.Client) ([]WebhookConfigResponse, error) {
	body, status, err := c.Get("/api/v1/webhook-configs")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var webhooks []WebhookConfigResponse
	if err := json.Unmarshal(body, &webhooks); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return webhooks, nil
}

// CreateWebhook creates a new webhook configuration.
func CreateWebhook(c *client.Client, req CreateWebhookRequest) (*WebhookConfigResponse, error) {
	body, status, err := c.Post("/api/v1/webhook-configs", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated && status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var webhook WebhookConfigResponse
	if err := json.Unmarshal(body, &webhook); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &webhook, nil
}

// DeleteWebhook deletes a webhook configuration by ID.
func DeleteWebhook(c *client.Client, id string) error {
	body, status, err := c.Delete("/api/v1/webhook-configs/" + id)
	if err != nil {
		return err
	}
	if status != http.StatusNoContent && status != http.StatusOK {
		return client.ParseError(status, body)
	}
	return nil
}

// ListWebhookDeliveries returns delivery records for a webhook config.
func ListWebhookDeliveries(c *client.Client, webhookID string) ([]WebhookDeliveryResponse, error) {
	body, status, err := c.Get("/api/v1/webhook-configs/" + webhookID + "/deliveries")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var deliveries []WebhookDeliveryResponse
	if err := json.Unmarshal(body, &deliveries); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return deliveries, nil
}
