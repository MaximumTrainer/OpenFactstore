package api

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// TypedAttestRequest is the body for POST /api/v2/trails/{trailId}/attestations.
type TypedAttestRequest struct {
	Type            string `json:"type"`
	Status          string `json:"status"`
	Name            string `json:"name,omitempty"`
	Details         string `json:"details,omitempty"`
	AttestationData string `json:"attestationData,omitempty"`
	EvidenceUrl     string `json:"evidenceUrl,omitempty"`
	GitCommitSha    string `json:"gitCommitSha,omitempty"`
	GitBranch       string `json:"gitBranch,omitempty"`
}

// RecordTypedAttestation records an attestation on a trail via the v2 API and returns it as
// recorded: a custom type's rules can change its status, so it is read back from the trail.
func RecordTypedAttestation(c *client.Client, trailID string, req TypedAttestRequest) (*AttestationResponse, error) {
	body, status, err := c.Post("/api/v2/trails/"+trailID+"/attestations", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated && status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var created CommandResult
	if err := json.Unmarshal(body, &created); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	attestations, err := ListAttestations(c, trailID)
	if err != nil {
		return nil, err
	}
	for i := range attestations {
		if attestations[i].ID == created.ID {
			return &attestations[i], nil
		}
	}
	return nil, fmt.Errorf("attestation %s was recorded but is not listed on trail %s", created.ID, trailID)
}
