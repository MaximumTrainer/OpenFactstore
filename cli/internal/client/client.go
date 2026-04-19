package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"
)

const (
	// maxAttempts is the total number of attempts (1 initial + 2 retries).
	maxAttempts = 3
	baseDelay   = time.Second
)

// Client is an HTTP client for the Factstore API.
// When QueryBaseURL is set, GET requests are routed to the query (read)
// service while mutating requests (POST/PUT/DELETE) go to BaseURL (the
// command/write service).  When QueryBaseURL is empty both read and write
// requests go to BaseURL, preserving backward compatibility.
type Client struct {
	BaseURL      string
	QueryBaseURL string
	Token        string
	httpClient   *http.Client
}

// New creates a new Client. Returns an error if baseURL uses http:// with a
// non-localhost host, to prevent sending tokens over plaintext connections.
func New(baseURL, token string) (*Client, error) {
	if err := validateURL(baseURL); err != nil {
		return nil, err
	}
	return &Client{
		BaseURL: strings.TrimRight(baseURL, "/"),
		Token:   token,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}, nil
}

// NewWithQueryHost creates a Client that routes GET requests to queryBaseURL.
// If queryBaseURL is empty it falls back to baseURL for all operations.
func NewWithQueryHost(baseURL, queryBaseURL, token string) (*Client, error) {
	if err := validateURL(baseURL); err != nil {
		return nil, err
	}
	if queryBaseURL != "" {
		if err := validateURL(queryBaseURL); err != nil {
			return nil, err
		}
	}
	c := &Client{
		BaseURL:      strings.TrimRight(baseURL, "/"),
		Token:        token,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
	if queryBaseURL != "" {
		c.QueryBaseURL = strings.TrimRight(queryBaseURL, "/")
	}
	return c, nil
}

// validateURL rejects insecure (http://) connections to non-localhost hosts.
func validateURL(rawURL string) error {
	if strings.HasPrefix(rawURL, "http://") {
		u, err := url.Parse(rawURL)
		if err != nil || (u.Hostname() != "localhost" && u.Hostname() != "127.0.0.1") {
			return fmt.Errorf("insecure connection refused: use https:// (http:// is only allowed for localhost)")
		}
	}
	return nil
}

// reqFactory is a function that produces a fresh *http.Request for each attempt.
type reqFactory func() (*http.Request, error)

func (c *Client) do(build reqFactory) ([]byte, int, error) {
	var (
		resp *http.Response
		err  error
	)
	delay := baseDelay
	for attempt := 0; attempt < maxAttempts; attempt++ {
		if attempt > 0 {
			time.Sleep(delay)
			delay *= 2
		}
		var req *http.Request
		req, err = build()
		if err != nil {
			return nil, 0, err
		}
		resp, err = c.httpClient.Do(req)
		if err == nil {
			break
		}
	}
	if err != nil {
		return nil, 0, fmt.Errorf("request failed after %d attempts: %w", maxAttempts, err)
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, resp.StatusCode, fmt.Errorf("read response body: %w", err)
	}
	return data, resp.StatusCode, nil
}

func (c *Client) doRequest(method, path string, body interface{}) ([]byte, int, error) {
	return c.doRequestWithBase(method, c.BaseURL, path, body)
}

func (c *Client) doRequestWithBase(method, base, path string, body interface{}) ([]byte, int, error) {
	// Pre-marshal the body once so each retry reuses the same bytes.
	var bodyBytes []byte
	if body != nil {
		var err error
		bodyBytes, err = json.Marshal(body)
		if err != nil {
			return nil, 0, fmt.Errorf("marshal request body: %w", err)
		}
	}

	return c.do(func() (*http.Request, error) {
		var bodyReader io.Reader
		if bodyBytes != nil {
			bodyReader = bytes.NewReader(bodyBytes)
		}
		reqURL := base + path
		req, err := http.NewRequest(method, reqURL, bodyReader)
		if err != nil {
			return nil, err
		}
		if bodyBytes != nil {
			req.Header.Set("Content-Type", "application/json")
		}
		req.Header.Set("Accept", "application/json")
		if c.Token != "" {
			req.Header.Set("Authorization", "Bearer "+c.Token)
		}
		return req, nil
	})
}

// Get performs a GET request.  When a QueryBaseURL is configured the request
// is routed to the query (read) service; otherwise it goes to BaseURL.
func (c *Client) Get(path string) ([]byte, int, error) {
	base := c.BaseURL
	if c.QueryBaseURL != "" {
		base = c.QueryBaseURL
	}
	return c.doRequestWithBase(http.MethodGet, base, path, nil)
}

// Post performs a POST request with a JSON body.
func (c *Client) Post(path string, body interface{}) ([]byte, int, error) {
	return c.doRequest(http.MethodPost, path, body)
}

// Put performs a PUT request with a JSON body.
func (c *Client) Put(path string, body interface{}) ([]byte, int, error) {
	return c.doRequest(http.MethodPut, path, body)
}

// Delete performs a DELETE request.
func (c *Client) Delete(path string) ([]byte, int, error) {
	return c.doRequest(http.MethodDelete, path, nil)
}

// PostMultipart uploads a file as multipart/form-data.
func (c *Client) PostMultipart(path, fieldName, filePath string) ([]byte, int, error) {
	f, err := os.Open(filePath)
	if err != nil {
		return nil, 0, fmt.Errorf("open file %s: %w", filePath, err)
	}
	defer f.Close()

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	// Use only the base filename to avoid leaking local filesystem paths.
	part, err := writer.CreateFormFile(fieldName, filepath.Base(filePath))
	if err != nil {
		return nil, 0, fmt.Errorf("create form file: %w", err)
	}
	if _, err = io.Copy(part, f); err != nil {
		return nil, 0, fmt.Errorf("copy file content: %w", err)
	}
	if err = writer.Close(); err != nil {
		return nil, 0, fmt.Errorf("close multipart writer: %w", err)
	}

	// Buffer the multipart body so retries can reuse it.
	multipartBytes := buf.Bytes()
	contentType := writer.FormDataContentType()

	return c.do(func() (*http.Request, error) {
		reqURL := c.BaseURL + path
		req, err := http.NewRequest(http.MethodPost, reqURL, bytes.NewReader(multipartBytes))
		if err != nil {
			return nil, err
		}
		req.Header.Set("Content-Type", contentType)
		req.Header.Set("Accept", "application/json")
		if c.Token != "" {
			req.Header.Set("Authorization", "Bearer "+c.Token)
		}
		return req, nil
	})
}

// ParseError extracts a user-friendly error from an API response body.
func ParseError(statusCode int, body []byte) error {
	var apiErr struct {
		Message string `json:"message"`
		Error   string `json:"error"`
	}
	if json.Unmarshal(body, &apiErr) == nil && apiErr.Message != "" {
		return fmt.Errorf("API error %d: %s", statusCode, apiErr.Message)
	}
	if json.Unmarshal(body, &apiErr) == nil && apiErr.Error != "" {
		return fmt.Errorf("API error %d: %s", statusCode, apiErr.Error)
	}
	if len(body) > 0 {
		return fmt.Errorf("API error %d: %s", statusCode, string(body))
	}
	return fmt.Errorf("API error %d", statusCode)
}
