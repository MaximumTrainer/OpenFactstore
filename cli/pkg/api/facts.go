package api

import (
	"crypto/ed25519"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"net/http"
	"os"
	"regexp"
	"strings"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
	"github.com/MaximumTrainer/Factstore/cli/pkg/masking"
)

// FactPushRequest is a fact to send (#238). Tool is the payload, as JSON.
type FactPushRequest struct {
	FactID     string
	Sha256     string
	Repository string
	SchemaRef  string
	Tool       []byte
}

// FactReceipt is what Factstore returns once the fact is appended (#186).
type FactReceipt struct {
	FactID      string `json:"fact_id"`
	SchemaRef   string `json:"schema_ref"`
	IngestedAt  string `json:"ingested_at"`
	ReceiptHash string `json:"receipt_hash"`
	LedgerIndex int64  `json:"ledger_index"`
}

// SigningKey is the runner's Ed25519 key and the id it is registered under (#187).
type SigningKey struct {
	ID  string
	key ed25519.PrivateKey
}

// LoadSigningKey reads an Ed25519 private key in PKCS#8 PEM (as `openssl genpkey -algorithm ed25519` writes).
func LoadSigningKey(pemBytes []byte, keyID string) (*SigningKey, error) {
	block, _ := pem.Decode(pemBytes)
	if block == nil {
		return nil, errors.New("signing key is not PEM")
	}
	parsed, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("signing key: %w", err)
	}
	key, ok := parsed.(ed25519.PrivateKey)
	if !ok {
		return nil, errors.New("signing key must be an Ed25519 key")
	}
	if keyID == "" {
		return nil, errors.New("a signing key id is required")
	}
	return &SigningKey{ID: keyID, key: key}, nil
}

// LoadSigningKeyFile reads the key from a file.
func LoadSigningKeyFile(path, keyID string) (*SigningKey, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	return LoadSigningKey(raw, keyID)
}

var schemaURN = regexp.MustCompile(`^urn:schema:([a-z0-9][a-z0-9._-]{0,127}):v([1-9][0-9]{0,5})$`)

// PushFact masks, signs and sends a fact (#238).
//
// The tool payload is masked with the server's ruleset (#198), including fields the fact's schema
// marks "x-sensitive" when the schema can be read. The fact is then serialised once, and those
// exact bytes are both signed (X-Signature, base64url Ed25519) and sent, so the signature covers
// what Factstore stores (#187).
func PushFact(c *client.Client, req FactPushRequest, key *SigningKey) (*FactReceipt, error) {
	tool, err := masking.Parse(req.Tool)
	if err != nil {
		return nil, fmt.Errorf("tool payload is not JSON: %w", err)
	}
	if _, ok := tool.(*masking.Object); !ok {
		return nil, errors.New("tool payload must be a JSON object")
	}
	masked, _ := masking.BuiltIn().Scan(tool, sensitivePointers(c, req.SchemaRef))

	sha := strings.TrimPrefix(req.Sha256, "sha256:")
	subject := map[string]any{"sha256": sha}
	if req.Repository != "" {
		subject["repository"] = req.Repository
	}
	body, err := json.Marshal(struct {
		FactID     string            `json:"fact_id"`
		Subject    map[string]any    `json:"subject"`
		Governance map[string]string `json:"governance"`
		Tool       masking.Value     `json:"tool"`
	}{req.FactID, subject, map[string]string{"schema_ref": req.SchemaRef}, masked})
	if err != nil {
		return nil, err
	}

	signature := base64.RawURLEncoding.EncodeToString(ed25519.Sign(key.key, body))
	resp, status, err := c.PostRaw("/api/v1/commands/facts", body, map[string]string{
		"X-Signature":        signature,
		"X-Signature-Key-Id": key.ID,
	})
	if err != nil {
		return nil, err
	}
	if status != http.StatusAccepted {
		return nil, factError(status, resp)
	}
	var receipt FactReceipt
	if err := json.Unmarshal(resp, &receipt); err != nil {
		return nil, fmt.Errorf("parse receipt: %w", err)
	}
	return &receipt, nil
}

// factError names the server's refusal and every violation it reported.
func factError(status int, body []byte) error {
	var refusal struct {
		Error      string `json:"error"`
		Message    string `json:"message"`
		Violations []struct {
			Path    string `json:"path"`
			Message string `json:"message"`
		} `json:"violations"`
	}
	if json.Unmarshal(body, &refusal) != nil || refusal.Error == "" {
		return client.ParseError(status, body)
	}
	parts := []string{fmt.Sprintf("fact refused (%d %s)", status, refusal.Error)}
	if refusal.Message != "" {
		parts = append(parts, refusal.Message)
	}
	for _, v := range refusal.Violations {
		parts = append(parts, v.Path+": "+v.Message)
	}
	return errors.New(strings.Join(parts, "; "))
}

// sensitivePointers reads the fact's schema and returns its "x-sensitive" fields. If the schema
// cannot be read (no permission, not found), rule-based masking still applies and the server's
// reject mode catches anything left.
func sensitivePointers(c *client.Client, ref string) []string {
	m := schemaURN.FindStringSubmatch(ref)
	if m == nil {
		return nil
	}
	body, status, err := c.Get(fmt.Sprintf("/api/v1/queries/schemas/%s/v%s", m[1], m[2]))
	if err != nil || status != http.StatusOK {
		fmt.Fprintf(os.Stderr, "warning: could not read schema %s to find x-sensitive fields; masking by rules only\n", ref)
		return nil
	}
	var doc struct {
		Schema map[string]any `json:"schema"`
	}
	if json.Unmarshal(body, &doc) != nil || doc.Schema == nil {
		return nil
	}
	var found []string
	var walk func(node map[string]any, at string)
	walk = func(node map[string]any, at string) {
		if s, ok := node["x-sensitive"].(bool); ok && s {
			found = append(found, at)
		}
		if props, ok := node["properties"].(map[string]any); ok {
			for name, child := range props {
				if m, ok := child.(map[string]any); ok {
					walk(m, at+"/"+strings.ReplaceAll(strings.ReplaceAll(name, "~", "~0"), "/", "~1"))
				}
			}
		}
		if items, ok := node["items"].(map[string]any); ok {
			walk(items, at+"/*")
		}
		if extra, ok := node["additionalProperties"].(map[string]any); ok {
			walk(extra, at+"/*")
		}
	}
	walk(doc.Schema, "")
	return found
}
