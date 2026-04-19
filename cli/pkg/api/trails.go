package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// TrailResponse mirrors the backend TrailResponse DTO.
type TrailResponse struct {
	ID                  string `json:"id"`
	FlowID              string `json:"flowId"`
	GitCommitSha        string `json:"gitCommitSha"`
	GitBranch           string `json:"gitBranch"`
	GitAuthor           string `json:"gitAuthor"`
	GitAuthorEmail      string `json:"gitAuthorEmail"`
	PullRequestID       string `json:"pullRequestId,omitempty"`
	PullRequestReviewer string `json:"pullRequestReviewer,omitempty"`
	DeploymentActor     string `json:"deploymentActor,omitempty"`
	Status              string `json:"status"`
	CreatedAt           string `json:"createdAt"`
	UpdatedAt           string `json:"updatedAt"`
}

// CreateTrailRequest is the body for POST /api/v2/trails.
type CreateTrailRequest struct {
	FlowID              string `json:"flowId"`
	GitCommitSha        string `json:"gitCommitSha"`
	GitBranch           string `json:"gitBranch"`
	GitAuthor           string `json:"gitAuthor"`
	GitAuthorEmail      string `json:"gitAuthorEmail"`
	PullRequestID       string `json:"pullRequestId,omitempty"`
	PullRequestReviewer string `json:"pullRequestReviewer,omitempty"`
	DeploymentActor     string `json:"deploymentActor,omitempty"`
}

// ListTrails returns all trails, optionally filtered by flowId (query path).
func ListTrails(c *client.Client, flowID string) ([]TrailResponse, error) {
	path := "/api/v2/trails"
	if flowID != "" {
		q := url.Values{}
		q.Set("flowId", flowID)
		path += "?" + q.Encode()
	}
	body, status, err := c.Get(path)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var trails []TrailResponse
	if err := json.Unmarshal(body, &trails); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return trails, nil
}

// GetTrail returns a single trail by ID (query path).
func GetTrail(c *client.Client, id string) (*TrailResponse, error) {
	body, status, err := c.Get("/api/v2/trails/" + id)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var trail TrailResponse
	if err := json.Unmarshal(body, &trail); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &trail, nil
}

// CreateTrail creates a new trail (command path).
func CreateTrail(c *client.Client, req CreateTrailRequest) (*CommandResult, error) {
	body, status, err := c.Post("/api/v2/trails", req)
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
