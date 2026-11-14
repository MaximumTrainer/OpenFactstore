package tests

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

// #238: `factstore fact push` masks a fact with the server's ruleset, signs the exact bytes it
// sends with the runner's Ed25519 key, and posts it, so the server's reject mode (#198) and
// signature check (#187) accept it unchanged.

type capturedFact struct {
	body      []byte
	signature string
	keyID     string
}

func factServer(t *testing.T, schema string, schemaStatus int, ingestStatus int, ingestBody string) (*httptest.Server, *capturedFact) {
	t.Helper()
	got := &capturedFact{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch {
		case r.Method == http.MethodGet && strings.HasPrefix(r.URL.Path, "/api/v1/queries/schemas/junit/v1"):
			w.WriteHeader(schemaStatus)
			w.Write([]byte(schema))
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/commands/facts":
			got.body, _ = io.ReadAll(r.Body)
			got.signature = r.Header.Get("X-Signature")
			got.keyID = r.Header.Get("X-Signature-Key-Id")
			w.WriteHeader(ingestStatus)
			w.Write([]byte(ingestBody))
		default:
			http.NotFound(w, r)
		}
	}))
	t.Cleanup(server.Close)
	return server, got
}

const accepted = `{"fact_id":"6f1c2a8e-4b0d-4f6e-9a51-2d3c4e5f6a7b","schema_ref":"urn:schema:junit:v1","ingested_at":"2026-11-12T22:00:00Z","receipt_hash":"abc123","ledger_index":42}`

func pushRequest(tool string) api.FactPushRequest {
	return api.FactPushRequest{
		FactID:     "6f1c2a8e-4b0d-4f6e-9a51-2d3c4e5f6a7b",
		Sha256:     strings.Repeat("e", 64),
		Repository: "org/service",
		SchemaRef:  "urn:schema:junit:v1",
		Tool:       []byte(tool),
	}
}

func newKey(t *testing.T) (ed25519.PublicKey, *api.SigningKey) {
	t.Helper()
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	der, _ := x509.MarshalPKCS8PrivateKey(priv)
	key, err := api.LoadSigningKey(pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der}), "runner-1")
	if err != nil {
		t.Fatal(err)
	}
	return pub, key
}

func TestPushMasksSignsAndReturnsTheReceipt(t *testing.T) {
	server, got := factServer(t, `{"type":"object"}`, http.StatusOK, http.StatusAccepted, accepted)
	pub, key := newKey(t)

	receipt, err := api.PushFact(mustNewClient(t, server.URL, "tok"), pushRequest(`{"log":"deploying with AKIAIOSFODNN7EXAMPLE for bob@example.org","passed":3}`), key)
	if err != nil {
		t.Fatal(err)
	}

	if receipt.ReceiptHash != "abc123" || receipt.LedgerIndex != 42 {
		t.Errorf("receipt not returned: %+v", receipt)
	}
	if strings.Contains(string(got.body), "AKIAIOSFODNN7EXAMPLE") || strings.Contains(string(got.body), "bob@example.org") {
		t.Fatalf("a secret left the runner: %s", got.body)
	}
	var sent map[string]any
	json.Unmarshal(got.body, &sent)
	if sent["tool"].(map[string]any)["log"] != "deploying with [MASKED:aws-access-key-id] for [MASKED:email]" {
		t.Errorf("unexpected masked tool: %v", sent["tool"])
	}
	if sent["governance"].(map[string]any)["schema_ref"] != "urn:schema:junit:v1" || sent["subject"].(map[string]any)["repository"] != "org/service" {
		t.Errorf("unexpected envelope: %s", got.body)
	}
	sig, err := base64.RawURLEncoding.DecodeString(got.signature)
	if err != nil || !ed25519.Verify(pub, got.body, sig) {
		t.Errorf("X-Signature does not verify over the exact body sent")
	}
	if got.keyID != "runner-1" {
		t.Errorf("X-Signature-Key-Id = %q", got.keyID)
	}
}

func TestPushMasksFieldsTheSchemaMarksSensitive(t *testing.T) {
	schema := `{"schema":{"type":"object","properties":{"db":{"type":"object","properties":{"password":{"type":"string","x-sensitive":true}}}}}}`
	server, got := factServer(t, schema, http.StatusOK, http.StatusAccepted, accepted)
	_, key := newKey(t)

	if _, err := api.PushFact(mustNewClient(t, server.URL, "tok"), pushRequest(`{"db":{"password":"correct horse"}}`), key); err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(got.body), "correct horse") || !strings.Contains(string(got.body), "[MASKED:sensitive-field]") {
		t.Errorf("sensitive field not masked: %s", got.body)
	}
}

func TestPushStillMasksWhenTheSchemaCannotBeRead(t *testing.T) {
	server, got := factServer(t, `{"error":"Forbidden"}`, http.StatusForbidden, http.StatusAccepted, accepted)
	_, key := newKey(t)

	if _, err := api.PushFact(mustNewClient(t, server.URL, "tok"), pushRequest(`{"log":"AKIAIOSFODNN7EXAMPLE"}`), key); err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(got.body), "AKIAIOSFODNN7EXAMPLE") {
		t.Errorf("rule-based masking must not depend on reading the schema: %s", got.body)
	}
}

func TestPushReportsTheServersRefusal(t *testing.T) {
	refusal := `{"error":"unmasked_secret_detected","schemaRef":"urn:schema:junit:v1","violations":[{"path":"/tool/db/password","message":"unmasked value matched masking rule sensitive-field"}]}`
	server, _ := factServer(t, `{}`, http.StatusOK, http.StatusBadRequest, refusal)
	_, key := newKey(t)

	_, err := api.PushFact(mustNewClient(t, server.URL, "tok"), pushRequest(`{"db":{"password":"x"}}`), key)
	if err == nil || !strings.Contains(err.Error(), "unmasked_secret_detected") || !strings.Contains(err.Error(), "/tool/db/password") {
		t.Fatalf("expected the refusal and its violation, got %v", err)
	}
}

func TestSigningKeyMustBeEd25519(t *testing.T) {
	if _, err := api.LoadSigningKey([]byte("-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n"), "k"); err == nil {
		t.Fatal("expected an error for a key that is not Ed25519")
	}
}
