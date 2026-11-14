package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"

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
	ArchivedAt  string `json:"archivedAt,omitempty"`
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
	return listEnvironments(c, "/api/v1/environments?size=1000")
}

// ListEnvironmentsIncludingArchived also returns archived environments (#245).
func ListEnvironmentsIncludingArchived(c *client.Client) ([]EnvironmentResponse, error) {
	return listEnvironments(c, "/api/v1/environments?size=1000&includeArchived=true")
}

func listEnvironments(c *client.Client, path string) ([]EnvironmentResponse, error) {
	body, status, err := c.Get(path)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	// The endpoint returns a page; older servers returned a bare array.
	var page struct {
		Items []EnvironmentResponse `json:"items"`
	}
	if err := json.Unmarshal(body, &page); err == nil {
		return page.Items, nil
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

// ArchiveEnvironment archives an environment (#245). Environments are never deleted: an
// archived one is hidden from listings and takes no new snapshots, and keeps its history.
func ArchiveEnvironment(c *client.Client, id string) (*EnvironmentResponse, error) {
	return postEnvironmentState(c, id, "archive")
}

// UnarchiveEnvironment brings an archived environment back.
func UnarchiveEnvironment(c *client.Client, id string) (*EnvironmentResponse, error) {
	return postEnvironmentState(c, id, "unarchive")
}

func postEnvironmentState(c *client.Client, id, action string) (*EnvironmentResponse, error) {
	body, status, err := c.Post("/api/v1/environments/"+url.PathEscape(id)+"/"+action, map[string]any{})
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
