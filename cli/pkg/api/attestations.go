package api

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// AttestationResponse mirrors the backend AttestationResponse DTO.
type AttestationResponse struct {
	ID                    string `json:"id"`
	TrailID               string `json:"trailId"`
	Type                  string `json:"type"`
	Status                string `json:"status"`
	EvidenceFileHash      string `json:"evidenceFileHash,omitempty"`
	EvidenceFileName      string `json:"evidenceFileName,omitempty"`
	EvidenceFileSizeBytes int64  `json:"evidenceFileSizeBytes,omitempty"`
	Details               string `json:"details,omitempty"`
	CreatedAt             string `json:"createdAt"`
}

// CreateAttestationRequest is the body for POST /api/v2/trails/{trailId}/attestations.
type CreateAttestationRequest struct {
	Type    string `json:"type"`
	Status  string `json:"status"`
	Details string `json:"details,omitempty"`
}

// ListAttestations returns all attestations for a trail (query path).
func ListAttestations(c *client.Client, trailID string) ([]AttestationResponse, error) {
	body, status, err := c.Get("/api/v2/trails/" + trailID + "/attestations")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var attestations []AttestationResponse
	if err := json.Unmarshal(body, &attestations); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return attestations, nil
}

// CreateAttestation creates a new attestation on a trail (command path).
func CreateAttestation(c *client.Client, trailID string, req CreateAttestationRequest) (*CommandResult, error) {
	body, status, err := c.Post("/api/v2/trails/"+trailID+"/attestations", req)
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

// UploadEvidence uploads an evidence file for an attestation (command path).
func UploadEvidence(c *client.Client, trailID, attestationID, filePath string) (*CommandResult, error) {
	path := fmt.Sprintf("/api/v2/trails/%s/attestations/%s/evidence", trailID, attestationID)
	body, status, err := c.PostMultipart(path, "file", filePath)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK && status != http.StatusCreated {
		return nil, client.ParseError(status, body)
	}
	var result CommandResult
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &result, nil
}
