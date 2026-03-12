package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// ArtifactResponse mirrors the backend ArtifactResponse DTO.
type ArtifactResponse struct {
	ID           string `json:"id"`
	TrailID      string `json:"trailId"`
	ImageName    string `json:"imageName"`
	ImageTag     string `json:"imageTag"`
	Sha256Digest string `json:"sha256Digest"`
	Registry     string `json:"registry,omitempty"`
	ReportedAt   string `json:"reportedAt"`
	ReportedBy   string `json:"reportedBy"`
}

// CreateArtifactRequest is the body for POST /api/v1/trails/{trailId}/artifacts.
type CreateArtifactRequest struct {
	ImageName    string `json:"imageName"`
	ImageTag     string `json:"imageTag"`
	Sha256Digest string `json:"sha256Digest"`
	Registry     string `json:"registry,omitempty"`
	ReportedBy   string `json:"reportedBy"`
}

// ListArtifacts returns all artifacts for a trail.
func ListArtifacts(c *client.Client, trailID string) ([]ArtifactResponse, error) {
	body, status, err := c.Get("/api/v1/trails/" + trailID + "/artifacts")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var artifacts []ArtifactResponse
	if err := json.Unmarshal(body, &artifacts); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return artifacts, nil
}

// FindArtifact looks up an artifact by SHA-256 digest.
func FindArtifact(c *client.Client, sha256 string) (*ArtifactResponse, error) {
	q := url.Values{}
	q.Set("sha256", sha256)
	body, status, err := c.Get("/api/v1/artifacts?" + q.Encode())
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var artifact ArtifactResponse
	if err := json.Unmarshal(body, &artifact); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &artifact, nil
}

// CreateArtifact creates a new artifact on a trail.
func CreateArtifact(c *client.Client, trailID string, req CreateArtifactRequest) (*ArtifactResponse, error) {
	body, status, err := c.Post("/api/v1/trails/"+trailID+"/artifacts", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated && status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var artifact ArtifactResponse
	if err := json.Unmarshal(body, &artifact); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &artifact, nil
}
