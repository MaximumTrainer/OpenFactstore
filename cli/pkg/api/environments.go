package api

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// EnvironmentResponse mirrors the backend EnvironmentResponse DTO.
type EnvironmentResponse struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Type        string `json:"type"`
	Description string `json:"description"`
	OrgSlug     string `json:"orgSlug,omitempty"`
	DriftPolicy string `json:"driftPolicy"`
	CreatedAt   string `json:"createdAt"`
	UpdatedAt   string `json:"updatedAt"`
}

// CreateEnvironmentRequest is the body for POST /api/v1/environments.
type CreateEnvironmentRequest struct {
	Name        string `json:"name"`
	Type        string `json:"type"`
	Description string `json:"description,omitempty"`
	OrgSlug     string `json:"orgSlug,omitempty"`
}

// ListEnvironments returns all environments.
func ListEnvironments(c *client.Client) ([]EnvironmentResponse, error) {
	body, status, err := c.Get("/api/v1/environments")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var envs []EnvironmentResponse
	if err := json.Unmarshal(body, &envs); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return envs, nil
}

// GetEnvironment returns a single environment by ID.
func GetEnvironment(c *client.Client, id string) (*EnvironmentResponse, error) {
	body, status, err := c.Get("/api/v1/environments/" + id)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var env EnvironmentResponse
	if err := json.Unmarshal(body, &env); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &env, nil
}

// CreateEnvironment creates a new environment.
func CreateEnvironment(c *client.Client, req CreateEnvironmentRequest) (*EnvironmentResponse, error) {
	body, status, err := c.Post("/api/v1/environments", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated && status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var env EnvironmentResponse
	if err := json.Unmarshal(body, &env); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &env, nil
}

// DeleteEnvironment deletes an environment by ID.
func DeleteEnvironment(c *client.Client, id string) error {
	body, status, err := c.Delete("/api/v1/environments/" + id)
	if err != nil {
		return err
	}
	if status != http.StatusNoContent && status != http.StatusOK {
		return client.ParseError(status, body)
	}
	return nil
}
