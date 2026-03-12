package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// AssertRequest is the body for POST /api/v1/assert.
type AssertRequest struct {
	Sha256Digest string `json:"sha256Digest"`
	FlowID       string `json:"flowId"`
}

// AssertResponse mirrors the backend AssertResponse DTO.
type AssertResponse struct {
	Sha256Digest            string   `json:"sha256Digest"`
	FlowID                  string   `json:"flowId"`
	Status                  string   `json:"status"`
	MissingAttestationTypes []string `json:"missingAttestationTypes"`
	FailedAttestationTypes  []string `json:"failedAttestationTypes"`
	Details                 string   `json:"details"`
}

// ChainOfCustodyResponse mirrors the backend chain-of-custody response.
type ChainOfCustodyResponse struct {
	Sha256Digest string             `json:"sha256Digest"`
	Artifact     *ArtifactResponse  `json:"artifact"`
	Trail        *TrailResponse     `json:"trail"`
	Attestations []AttestationResponse `json:"attestations"`
	Flow         *FlowResponse      `json:"flow"`
}

// Assert checks compliance for an artifact against a flow.
func Assert(c *client.Client, req AssertRequest) (*AssertResponse, error) {
	body, status, err := c.Post("/api/v1/assert", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var result AssertResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &result, nil
}

// GetChainOfCustody returns the full chain of custody for a SHA-256 digest.
func GetChainOfCustody(c *client.Client, sha256Digest string) (*ChainOfCustodyResponse, error) {
	body, status, err := c.Get("/api/v1/compliance/artifact/" + url.PathEscape(sha256Digest))
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var result ChainOfCustodyResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &result, nil
}
