package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// SearchResultItem mirrors the backend SearchResultItem DTO.
type SearchResultItem struct {
	Type        string            `json:"type"`
	ID          string            `json:"id"`
	Title       string            `json:"title"`
	Description string            `json:"description"`
	Metadata    map[string]string `json:"metadata,omitempty"`
}

// SearchResponse mirrors the backend SearchResponse DTO.
type SearchResponse struct {
	Results []SearchResultItem `json:"results"`
	Total   int                `json:"total"`
	Query   string             `json:"query"`
	Type    string             `json:"type,omitempty"`
}

// Search performs a full-text search across trails and artifacts.
func Search(c *client.Client, query, resultType string) (*SearchResponse, error) {
	q := url.Values{}
	q.Set("q", query)
	if resultType != "" {
		q.Set("type", resultType)
	}
	body, status, err := c.Get("/api/v1/search?" + q.Encode())
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var resp SearchResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &resp, nil
}
