package api

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// FlowResponse mirrors the backend FlowResponse DTO.
type FlowResponse struct {
	ID                       string   `json:"id"`
	Name                     string   `json:"name"`
	Description              string   `json:"description"`
	RequiredAttestationTypes []string `json:"requiredAttestationTypes"`
	CreatedAt                string   `json:"createdAt"`
	UpdatedAt                string   `json:"updatedAt"`
}

// CreateFlowRequest is the body for POST /api/v2/flows.
type CreateFlowRequest struct {
	Name                     string   `json:"name"`
	Description              string   `json:"description"`
	RequiredAttestationTypes []string `json:"requiredAttestationTypes"`
}

// UpdateFlowRequest is the body for PUT /api/v2/flows/{id}.
type UpdateFlowRequest struct {
	Name                     string   `json:"name,omitempty"`
	Description              string   `json:"description,omitempty"`
	RequiredAttestationTypes []string `json:"requiredAttestationTypes,omitempty"`
}

// CommandResult is the minimal response returned by CQRS command endpoints.
type CommandResult struct {
	ID        string `json:"id"`
	Status    string `json:"status"`
	Timestamp string `json:"timestamp"`
}

// ListFlows returns all flows (query path).
func ListFlows(c *client.Client) ([]FlowResponse, error) {
	body, status, err := c.Get("/api/v2/flows")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var flows []FlowResponse
	if err := json.Unmarshal(body, &flows); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return flows, nil
}

// GetFlow returns a single flow by ID (query path).
func GetFlow(c *client.Client, id string) (*FlowResponse, error) {
	body, status, err := c.Get("/api/v2/flows/" + id)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var flow FlowResponse
	if err := json.Unmarshal(body, &flow); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &flow, nil
}

// CreateFlow creates a new flow (command path).
func CreateFlow(c *client.Client, req CreateFlowRequest) (*CommandResult, error) {
	body, status, err := c.Post("/api/v2/flows", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated && status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var result CommandResult
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &result, nil
}

// UpdateFlow updates an existing flow (command path).
func UpdateFlow(c *client.Client, id string, req UpdateFlowRequest) (*CommandResult, error) {
	body, status, err := c.Put("/api/v2/flows/"+id, req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var result CommandResult
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &result, nil
}

// DeleteFlow deletes a flow by ID (command path).
func DeleteFlow(c *client.Client, id string) error {
	body, status, err := c.Delete("/api/v2/flows/" + id)
	if err != nil {
		return err
	}
	if status != http.StatusNoContent && status != http.StatusOK {
		return client.ParseError(status, body)
	}
	return nil
}
