package api

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// SnapshotArtifactItem represents one artifact entry in a snapshot.
type SnapshotArtifactItem struct {
	ArtifactSha256 string `json:"artifactSha256"`
	ArtifactName   string `json:"artifactName"`
	ArtifactTag    string `json:"artifactTag"`
	InstanceCount  int    `json:"instanceCount"`
}

// RecordSnapshotRequest is the body for POST /api/v1/environments/{id}/snapshots.
type RecordSnapshotRequest struct {
	RecordedBy string                 `json:"recordedBy"`
	Artifacts  []SnapshotArtifactItem `json:"artifacts"`
}

// EnvironmentSnapshotResponse mirrors the backend EnvironmentSnapshotResponse DTO.
type EnvironmentSnapshotResponse struct {
	ID            string                 `json:"id"`
	EnvironmentID string                 `json:"environmentId"`
	SnapshotIndex int64                  `json:"snapshotIndex"`
	RecordedAt    string                 `json:"recordedAt"`
	RecordedBy    string                 `json:"recordedBy"`
	Artifacts     []SnapshotArtifactItem `json:"artifacts"`
}

// SnapshotDiffEntry represents a single diff entry between snapshots.
type SnapshotDiffEntry struct {
	ArtifactName string `json:"artifactName"`
	ArtifactTag  string `json:"artifactTag"`
	Sha256From   string `json:"sha256From,omitempty"`
	Sha256To     string `json:"sha256To,omitempty"`
}

// SnapshotDiffResponse mirrors the backend SnapshotDiffResponse DTO.
type SnapshotDiffResponse struct {
	EnvironmentID     string              `json:"environmentId"`
	FromSnapshotIndex int64               `json:"fromSnapshotIndex"`
	ToSnapshotIndex   int64               `json:"toSnapshotIndex"`
	Added             []SnapshotDiffEntry `json:"added"`
	Removed           []SnapshotDiffEntry `json:"removed"`
	Updated           []SnapshotDiffEntry `json:"updated"`
	Unchanged         []SnapshotDiffEntry `json:"unchanged"`
}

// ListSnapshots returns all snapshots for an environment.
func ListSnapshots(c *client.Client, envID string) ([]EnvironmentSnapshotResponse, error) {
	body, status, err := c.Get("/api/v1/environments/" + envID + "/snapshots")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var snapshots []EnvironmentSnapshotResponse
	if err := json.Unmarshal(body, &snapshots); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return snapshots, nil
}

// GetSnapshot returns a single snapshot by environment ID and snapshot index.
func GetSnapshot(c *client.Client, envID string, index string) (*EnvironmentSnapshotResponse, error) {
	body, status, err := c.Get("/api/v1/environments/" + envID + "/snapshots/" + index)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var snapshot EnvironmentSnapshotResponse
	if err := json.Unmarshal(body, &snapshot); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &snapshot, nil
}

// RecordSnapshot records a new snapshot for an environment.
func RecordSnapshot(c *client.Client, envID string, req RecordSnapshotRequest) (*EnvironmentSnapshotResponse, error) {
	body, status, err := c.Post("/api/v1/environments/"+envID+"/snapshots", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated && status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var snapshot EnvironmentSnapshotResponse
	if err := json.Unmarshal(body, &snapshot); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &snapshot, nil
}

// DiffSnapshots returns the diff between two consecutive snapshots.
func DiffSnapshots(c *client.Client, envID string, from, to int64) (*SnapshotDiffResponse, error) {
	path := fmt.Sprintf("/api/v1/environments/%s/diff?from=%d&to=%d", envID, from, to)
	body, status, err := c.Get(path)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var diff SnapshotDiffResponse
	if err := json.Unmarshal(body, &diff); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &diff, nil
}
