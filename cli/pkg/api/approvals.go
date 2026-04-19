package api

import (
"encoding/json"
"fmt"
"net/http"
"net/url"

"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// ApprovalDecisionResponse mirrors the backend ApprovalDecisionResponse DTO.
type ApprovalDecisionResponse struct {
ID               string `json:"id"`
ApprovalID       string `json:"approvalId"`
ApproverIdentity string `json:"approverIdentity"`
Decision         string `json:"decision"`
Comments         string `json:"comments,omitempty"`
DecidedAt        string `json:"decidedAt"`
}

// ApprovalResponse mirrors the backend ApprovalResponse DTO.
type ApprovalResponse struct {
ID                string                     `json:"id"`
TrailID           string                     `json:"trailId"`
FlowID            string                     `json:"flowId"`
Status            string                     `json:"status"`
RequiredApprovers []string                   `json:"requiredApprovers"`
Comments          string                     `json:"comments,omitempty"`
RequestedAt       string                     `json:"requestedAt"`
Deadline          string                     `json:"deadline,omitempty"`
ResolvedAt        string                     `json:"resolvedAt,omitempty"`
Decisions         []ApprovalDecisionResponse `json:"decisions,omitempty"`
}

// CreateApprovalRequest is the body for POST /api/v1/trails/{trailId}/approvals.
type CreateApprovalRequest struct {
TrailID           string   `json:"trailId"`
RequiredApprovers []string `json:"requiredApprovers,omitempty"`
Comments          string   `json:"comments,omitempty"`
}

// ApproveRequest is the body for POST /api/v1/approvals/{id}/approve.
type ApproveRequest struct {
ApproverIdentity string `json:"approverIdentity"`
Comments         string `json:"comments,omitempty"`
}

// RejectRequest is the body for POST /api/v1/approvals/{id}/reject.
type RejectRequest struct {
ApproverIdentity string `json:"approverIdentity"`
Comments         string `json:"comments,omitempty"`
}

// ListApprovalsByTrail returns all approvals for a trail.
func ListApprovalsByTrail(c *client.Client, trailID string) ([]ApprovalResponse, error) {
body, status, err := c.Get("/api/v1/trails/" + trailID + "/approvals")
if err != nil {
return nil, err
}
if status != http.StatusOK {
return nil, client.ParseError(status, body)
}
var approvals []ApprovalResponse
if err := json.Unmarshal(body, &approvals); err != nil {
return nil, fmt.Errorf("parse response: %w", err)
}
return approvals, nil
}

// ListApprovals returns approvals optionally filtered by status.
func ListApprovals(c *client.Client, statusFilter string) ([]ApprovalResponse, error) {
path := "/api/v1/approvals"
if statusFilter != "" {
q := url.Values{}
q.Set("status", statusFilter)
path += "?" + q.Encode()
}
body, httpStatus, err := c.Get(path)
if err != nil {
return nil, err
}
if httpStatus != http.StatusOK {
return nil, client.ParseError(httpStatus, body)
}
var approvals []ApprovalResponse
if err := json.Unmarshal(body, &approvals); err != nil {
return nil, fmt.Errorf("parse response: %w", err)
}
return approvals, nil
}

// GetApproval returns a single approval by ID.
func GetApproval(c *client.Client, id string) (*ApprovalResponse, error) {
body, status, err := c.Get("/api/v1/approvals/" + id)
if err != nil {
return nil, err
}
if status != http.StatusOK {
return nil, client.ParseError(status, body)
}
var approval ApprovalResponse
if err := json.Unmarshal(body, &approval); err != nil {
return nil, fmt.Errorf("parse response: %w", err)
}
return &approval, nil
}

// RequestApproval creates an approval request for a trail.
func RequestApproval(c *client.Client, trailID string, req CreateApprovalRequest) (*ApprovalResponse, error) {
body, status, err := c.Post("/api/v1/trails/"+trailID+"/approvals", req)
if err != nil {
return nil, err
}
if status != http.StatusCreated && status != http.StatusOK {
return nil, client.ParseError(status, body)
}
var approval ApprovalResponse
if err := json.Unmarshal(body, &approval); err != nil {
return nil, fmt.Errorf("parse response: %w", err)
}
return &approval, nil
}

// ApproveApproval approves an approval request.
func ApproveApproval(c *client.Client, id string, req ApproveRequest) (*ApprovalResponse, error) {
body, status, err := c.Post("/api/v1/approvals/"+id+"/approve", req)
if err != nil {
return nil, err
}
if status != http.StatusOK && status != http.StatusCreated {
return nil, client.ParseError(status, body)
}
var approval ApprovalResponse
if err := json.Unmarshal(body, &approval); err != nil {
return nil, fmt.Errorf("parse response: %w", err)
}
return &approval, nil
}

// RejectApproval rejects an approval request.
func RejectApproval(c *client.Client, id string, req RejectRequest) (*ApprovalResponse, error) {
body, status, err := c.Post("/api/v1/approvals/"+id+"/reject", req)
if err != nil {
return nil, err
}
if status != http.StatusOK && status != http.StatusCreated {
return nil, client.ParseError(status, body)
}
var approval ApprovalResponse
if err := json.Unmarshal(body, &approval); err != nil {
return nil, fmt.Errorf("parse response: %w", err)
}
return &approval, nil
}
