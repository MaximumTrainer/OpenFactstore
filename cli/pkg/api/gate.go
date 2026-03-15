package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strconv"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// GateEvaluateRequest is the body for POST /api/v1/gate/evaluate.
type GateEvaluateRequest struct {
	ArtifactSha256 string `json:"artifactSha256"`
	Environment    string `json:"environment,omitempty"`
	FlowID         string `json:"flowId,omitempty"`
}

// GateEvaluateResponse mirrors the backend gate evaluation response.
type GateEvaluateResponse struct {
	Allowed    bool   `json:"allowed"`
	Reason     string `json:"reason"`
	PolicyName string `json:"policyName"`
}

// GateResultResponse mirrors a single gate result entry.
type GateResultResponse struct {
	ID             string `json:"id"`
	ArtifactSha256 string `json:"artifactSha256"`
	Environment    string `json:"environment"`
	Allowed        bool   `json:"allowed"`
	Reason         string `json:"reason"`
	PolicyName     string `json:"policyName"`
	EvaluatedAt    string `json:"evaluatedAt"`
}

// EvaluateGate calls POST /api/v1/gate/evaluate and returns the gate decision.
func EvaluateGate(c *client.Client, req GateEvaluateRequest) (*GateEvaluateResponse, error) {
	body, status, err := c.Post("/api/v1/gate/evaluate", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK && status != http.StatusCreated {
		return nil, client.ParseError(status, body)
	}
	var result GateEvaluateResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &result, nil
}

// ListGateResults calls GET /api/v1/gate/results with optional filters.
func ListGateResults(c *client.Client, environment string, limit int) ([]GateResultResponse, error) {
	q := url.Values{}
	if environment != "" {
		q.Set("environment", environment)
	}
	if limit > 0 {
		q.Set("limit", strconv.Itoa(limit))
	}
	path := "/api/v1/gate/results"
	if len(q) > 0 {
		path += "?" + q.Encode()
	}
	body, status, err := c.Get(path)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var results []GateResultResponse
	if err := json.Unmarshal(body, &results); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return results, nil
}
