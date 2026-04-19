package api

import (
"encoding/json"
"fmt"
"net/http"

"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// PolicyResponse mirrors the backend PolicyResponse DTO.
type PolicyResponse struct {
ID                       string   `json:"id"`
Name                     string   `json:"name"`
EnforceProvenance        bool     `json:"enforceProvenance"`
EnforceTrailCompliance   bool     `json:"enforceTrailCompliance"`
RequiredAttestationTypes []string `json:"requiredAttestationTypes"`
OrgSlug                  string   `json:"orgSlug,omitempty"`
CreatedAt                string   `json:"createdAt"`
UpdatedAt                string   `json:"updatedAt"`
}

// CreatePolicyRequest is the body for POST /api/v1/policies.
type CreatePolicyRequest struct {
Name                     string   `json:"name"`
EnforceProvenance        bool     `json:"enforceProvenance,omitempty"`
EnforceTrailCompliance   bool     `json:"enforceTrailCompliance,omitempty"`
RequiredAttestationTypes []string `json:"requiredAttestationTypes,omitempty"`
}

// PolicyAttachmentResponse mirrors the backend PolicyAttachmentResponse DTO.
type PolicyAttachmentResponse struct {
ID            string `json:"id"`
PolicyID      string `json:"policyId"`
EnvironmentID string `json:"environmentId"`
CreatedAt     string `json:"createdAt"`
}

// CreatePolicyAttachmentRequest is the body for POST /api/v1/policy-attachments.
type CreatePolicyAttachmentRequest struct {
PolicyID      string `json:"policyId"`
EnvironmentID string `json:"environmentId"`
}

// ListPolicies returns all policies.
func ListPolicies(c *client.Client) ([]PolicyResponse, error) {
body, status, err := c.Get("/api/v1/policies")
if err != nil {
return nil, err
}
if status != http.StatusOK {
return nil, client.ParseError(status, body)
}
var policies []PolicyResponse
if err := json.Unmarshal(body, &policies); err != nil {
return nil, fmt.Errorf("parse response: %w", err)
}
return policies, nil
}

// GetPolicy returns a single policy by ID.
func GetPolicy(c *client.Client, id string) (*PolicyResponse, error) {
body, status, err := c.Get("/api/v1/policies/" + id)
if err != nil {
return nil, err
}
if status != http.StatusOK {
return nil, client.ParseError(status, body)
}
var policy PolicyResponse
if err := json.Unmarshal(body, &policy); err != nil {
return nil, fmt.Errorf("parse response: %w", err)
}
return &policy, nil
}

// CreatePolicy creates a new policy.
func CreatePolicy(c *client.Client, req CreatePolicyRequest) (*PolicyResponse, error) {
body, status, err := c.Post("/api/v1/policies", req)
if err != nil {
return nil, err
}
if status != http.StatusCreated && status != http.StatusOK {
return nil, client.ParseError(status, body)
}
var policy PolicyResponse
if err := json.Unmarshal(body, &policy); err != nil {
return nil, fmt.Errorf("parse response: %w", err)
}
return &policy, nil
}

// DeletePolicy deletes a policy by ID.
func DeletePolicy(c *client.Client, id string) error {
body, status, err := c.Delete("/api/v1/policies/" + id)
if err != nil {
return err
}
if status != http.StatusNoContent && status != http.StatusOK {
return client.ParseError(status, body)
}
return nil
}

// LinkPolicy attaches a policy to an environment.
func LinkPolicy(c *client.Client, req CreatePolicyAttachmentRequest) (*PolicyAttachmentResponse, error) {
body, status, err := c.Post("/api/v1/policy-attachments", req)
if err != nil {
return nil, err
}
if status != http.StatusCreated && status != http.StatusOK {
return nil, client.ParseError(status, body)
}
var attachment PolicyAttachmentResponse
if err := json.Unmarshal(body, &attachment); err != nil {
return nil, fmt.Errorf("parse response: %w", err)
}
return &attachment, nil
}

// UnlinkPolicy deletes a policy attachment by ID.
func UnlinkPolicy(c *client.Client, id string) error {
body, status, err := c.Delete("/api/v1/policy-attachments/" + id)
if err != nil {
return err
}
if status != http.StatusNoContent && status != http.StatusOK {
return client.ParseError(status, body)
}
return nil
}
