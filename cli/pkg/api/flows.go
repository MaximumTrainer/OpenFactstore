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

// CreateFlowRequest is the body for POST /api/v1/flows.
type CreateFlowRequest struct {
	Name                     string   `json:"name"`
	Description              string   `json:"description"`
	RequiredAttestationTypes []string `json:"requiredAttestationTypes"`
}

// UpdateFlowRequest is the body for PUT /api/v1/flows/{id}.
type UpdateFlowRequest struct {
	Name                     string   `json:"name,omitempty"`
	Description              string   `json:"description,omitempty"`
	RequiredAttestationTypes []string `json:"requiredAttestationTypes,omitempty"`
}

// ListFlows returns all flows.
func ListFlows(c *client.Client) ([]FlowResponse, error) {
	body, status, err := c.Get("/api/v1/flows")
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

// GetFlow returns a single flow by ID.
func GetFlow(c *client.Client, id string) (*FlowResponse, error) {
	body, status, err := c.Get("/api/v1/flows/" + id)
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

// CreateFlow creates a new flow.
func CreateFlow(c *client.Client, req CreateFlowRequest) (*FlowResponse, error) {
	body, status, err := c.Post("/api/v1/flows", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated && status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var flow FlowResponse
	if err := json.Unmarshal(body, &flow); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &flow, nil
}

// UpdateFlow updates an existing flow.
func UpdateFlow(c *client.Client, id string, req UpdateFlowRequest) (*FlowResponse, error) {
	body, status, err := c.Put("/api/v1/flows/"+id, req)
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

// DeleteFlow deletes a flow by ID.
func DeleteFlow(c *client.Client, id string) error {
	body, status, err := c.Delete("/api/v1/flows/" + id)
	if err != nil {
		return err
	}
	if status != http.StatusNoContent && status != http.StatusOK {
		return client.ParseError(status, body)
	}
	return nil
}
