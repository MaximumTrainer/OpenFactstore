package api

import (
	"crypto/rand"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// GateCheckRequest asks whether an artifact may be deployed to an environment (#193).
type GateCheckRequest struct {
	ArtifactSha256    string
	TargetEnvironment string
	FlowID            string
	RequiredPolicies  []string
}

// GateCheckResult is the gate decision, and who made it.
type GateCheckResult struct {
	Approved bool `json:"approved"`
	// NONE when the evidence decided; FAIL_OPEN or FAIL_CLOSED when the risk tier did.
	FallbackMode string `json:"fallback_mode"`
	// "server" when Factstore answered, "client" when it could not be reached.
	DecidedBy string `json:"decided_by"`
	// TIER1, STANDARD, or UNKNOWN when the CLI has never seen the environment.
	Tier                string   `json:"tier"`
	Reason              string   `json:"reason"`
	MissingAttestations []string `json:"missing_attestations,omitempty"`
	AuditAlertID        string   `json:"audit_alert_id,omitempty"`
	// Set on a client-side fallback: the id it will be reported under once Factstore is back.
	ReportID string `json:"report_id,omitempty"`
}

// FallbackStore keeps, in a directory, what the CLI needs when Factstore is unreachable: each
// environment's last-known risk tier, and the fallback decisions not yet reported.
type FallbackStore struct {
	path string
	mu   sync.Mutex
}

type fallbackState struct {
	Tiers   map[string]string `json:"tiers"`
	Pending []map[string]any  `json:"pending"`
}

// OpenFallbackStore keeps its state in dir/gate-fallback.json.
func OpenFallbackStore(dir string) *FallbackStore {
	return &FallbackStore{path: filepath.Join(dir, "gate-fallback.json")}
}

func (s *FallbackStore) load() fallbackState {
	state := fallbackState{Tiers: map[string]string{}}
	if raw, err := os.ReadFile(s.path); err == nil {
		_ = json.Unmarshal(raw, &state)
	}
	if state.Tiers == nil {
		state.Tiers = map[string]string{}
	}
	return state
}

func (s *FallbackStore) save(state fallbackState) error {
	if err := os.MkdirAll(filepath.Dir(s.path), 0o700); err != nil {
		return err
	}
	raw, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(s.path, raw, 0o600)
}

// Pending returns the fallback decisions not yet reported to Factstore.
func (s *FallbackStore) Pending() []map[string]any {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.load().Pending
}

var errUnreachable = errors.New("factstore unreachable")

// outage reports whether a call failed because Factstore could not be reached: no connection, a
// timeout, or a gateway in front of it answering 502, 503 or 504. A refused credential is not one.
func outage(err error, status int) bool {
	if err != nil {
		return true
	}
	return status == http.StatusBadGateway || status == http.StatusServiceUnavailable || status == http.StatusGatewayTimeout
}

// CheckGate evaluates a deployment gate (#193).
//
// When Factstore answers, its decision stands, including any fallback it applied itself. When it
// cannot be reached, the CLI decides by the environment's risk tier, as cached on the last
// successful call: STANDARD fails open; TIER1, or a tier the CLI has never seen, fails closed. The
// decision is kept, and reported to Factstore on the next successful call, so it is audited and
// alerted on like a server-side fallback.
func CheckGate(c *client.Client, req GateCheckRequest, store *FallbackStore, now time.Time) (*GateCheckResult, error) {
	tier, err := environmentTier(c, req.TargetEnvironment)
	if errors.Is(err, errUnreachable) {
		return fallBack(store, req, now, "Factstore could not be reached to read the environment's tier")
	}
	if err != nil {
		return nil, err
	}
	if tier != "" {
		if err := store.remember(req.TargetEnvironment, tier); err != nil {
			return nil, err
		}
	}

	result, err := evaluate(c, req)
	if errors.Is(err, errUnreachable) {
		return fallBack(store, req, now, "Factstore could not be reached to evaluate the gate")
	}
	if err != nil {
		return nil, err
	}
	// Factstore is reachable: deliver any fallbacks decided while it was not. A failure here
	// leaves them pending for next time and does not change this decision.
	_ = flushReports(c, store)
	return result, nil
}

func (s *FallbackStore) remember(environment, tier string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	state := s.load()
	state.Tiers[environment] = tier
	return s.save(state)
}

func environmentTier(c *client.Client, name string) (string, error) {
	body, status, err := c.Get("/api/v1/environments?size=1000")
	if outage(err, status) {
		return "", errUnreachable
	}
	if status != http.StatusOK {
		return "", client.ParseError(status, body)
	}
	type env struct {
		Name string `json:"name"`
		Tier string `json:"tier"`
	}
	var page struct {
		Items []env `json:"items"`
	}
	var list []env
	if json.Unmarshal(body, &page) == nil && page.Items != nil {
		list = page.Items
	} else if err := json.Unmarshal(body, &list); err != nil {
		return "", fmt.Errorf("parse environments: %w", err)
	}
	for _, e := range list {
		if e.Name == name {
			return e.Tier, nil
		}
	}
	return "", nil
}

func evaluate(c *client.Client, req GateCheckRequest) (*GateCheckResult, error) {
	payload := map[string]any{
		"artifact_sha256":    req.ArtifactSha256,
		"target_environment": req.TargetEnvironment,
		"flow_id":            req.FlowID,
	}
	if len(req.RequiredPolicies) > 0 {
		payload["required_policies"] = req.RequiredPolicies
	}
	body, status, err := c.Post("/api/v1/queries/gates/evaluate", payload)
	if outage(err, status) {
		return nil, errUnreachable
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var decision struct {
		Status              string   `json:"status"`
		FallbackMode        string   `json:"fallback_mode"`
		AuditAlertID        string   `json:"audit_alert_id"`
		MissingAttestations []string `json:"missing_attestations"`
		DecisionTree        struct {
			Tier   string `json:"tier"`
			Reason string `json:"reason"`
		} `json:"decision_tree"`
	}
	if err := json.Unmarshal(body, &decision); err != nil {
		return nil, fmt.Errorf("parse gate decision: %w", err)
	}
	return &GateCheckResult{
		Approved:            decision.Status == "APPROVED",
		FallbackMode:        decision.FallbackMode,
		DecidedBy:           "server",
		Tier:                decision.DecisionTree.Tier,
		Reason:              decision.DecisionTree.Reason,
		MissingAttestations: decision.MissingAttestations,
		AuditAlertID:        decision.AuditAlertID,
	}, nil
}

func fallBack(store *FallbackStore, req GateCheckRequest, now time.Time, why string) (*GateCheckResult, error) {
	store.mu.Lock()
	defer store.mu.Unlock()
	state := store.load()
	tier := state.Tiers[req.TargetEnvironment]
	if tier == "" {
		tier = "UNKNOWN"
	}
	mode, approved, verb := "FAIL_CLOSED", false, "closed"
	if tier == "STANDARD" {
		mode, approved, verb = "FAIL_OPEN", true, "open"
	}
	reason := fmt.Sprintf("%s; %s fails %s", why, tier, verb)
	// The server knows two tiers; an environment the CLI never saw is treated as the strictest.
	reportedTier := tier
	if reportedTier == "UNKNOWN" {
		reportedTier = "TIER1"
	}
	id := newReportID()
	state.Pending = append(state.Pending, map[string]any{
		"report_id":          id,
		"artifact_sha256":    req.ArtifactSha256,
		"target_environment": req.TargetEnvironment,
		"flow_id":            req.FlowID,
		"tier":               reportedTier,
		"fallback_mode":      mode,
		"reason":             reason,
		"decided_at":         now.UTC().Format(time.RFC3339),
	})
	if err := store.save(state); err != nil {
		return nil, err
	}
	return &GateCheckResult{Approved: approved, FallbackMode: mode, DecidedBy: "client", Tier: tier, Reason: reason, ReportID: id}, nil
}

// flushReports sends pending fallback reports; each is removed once Factstore has recorded it.
func flushReports(c *client.Client, store *FallbackStore) error {
	store.mu.Lock()
	defer store.mu.Unlock()
	state := store.load()
	var kept []map[string]any
	var firstErr error
	for _, report := range state.Pending {
		body, status, err := c.Post("/api/v1/commands/gates/fallback-reports", report)
		switch {
		case outage(err, status):
			kept = append(kept, report)
			if firstErr == nil {
				firstErr = errUnreachable
			}
		case status == http.StatusCreated || status == http.StatusOK:
			// Recorded, or already recorded: either way it is delivered.
		default:
			kept = append(kept, report)
			if firstErr == nil {
				firstErr = client.ParseError(status, body)
			}
		}
	}
	state.Pending = kept
	if err := store.save(state); err != nil {
		return err
	}
	return firstErr
}

func newReportID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}
