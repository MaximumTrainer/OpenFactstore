package tests

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

// #193: when Factstore cannot be reached at all, the CLI decides the gate itself from the
// environment's risk tier, cached on the last successful call. STANDARD fails open, TIER1 (or
// an unknown tier) fails closed. Each such decision is kept and reported once Factstore is
// reachable again, so it is audited and alerted on like a server-side fallback.

const sha = "sha256:a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1"

var at = time.Date(2026, 11, 12, 17, 0, 0, 0, time.UTC)

// fakeFactstore serves the environment list, the gate and the fallback-report endpoints.
type fakeFactstore struct {
	mu      sync.Mutex
	tier    string
	status  string
	reports []map[string]any
}

func (f *fakeFactstore) handler(t *testing.T) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		f.mu.Lock()
		defer f.mu.Unlock()
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/api/v1/environments":
			json.NewEncoder(w).Encode(map[string]any{
				"items": []map[string]any{{"id": "e1", "name": "prod", "type": "K8S", "tier": f.tier}},
			})
		case "/api/v1/queries/gates/evaluate":
			var body map[string]any
			json.NewDecoder(r.Body).Decode(&body)
			if body["target_environment"] != "prod" || body["artifact_sha256"] != sha {
				t.Errorf("unexpected gate request: %v", body)
			}
			json.NewEncoder(w).Encode(map[string]any{
				"status": f.status, "fallback_mode": "NONE",
				"decision_tree":        map[string]any{"tier": f.tier, "outcome": "EVALUATED"},
				"missing_attestations": []string{},
			})
		case "/api/v1/commands/gates/fallback-reports":
			raw, _ := io.ReadAll(r.Body)
			var report map[string]any
			json.Unmarshal(raw, &report)
			f.reports = append(f.reports, report)
			w.WriteHeader(http.StatusCreated)
			w.Write([]byte(`{"audit_alert_id":"alert-1"}`))
		default:
			http.NotFound(w, r)
		}
	})
}

func gateRequest() api.GateCheckRequest {
	return api.GateCheckRequest{ArtifactSha256: sha, TargetEnvironment: "prod", FlowID: "f1"}
}

// warmUp makes one successful call so the environment's tier is cached, then takes Factstore away.
func warmUp(t *testing.T, tier string) (*api.FallbackStore, *client.Client) {
	t.Helper()
	fake := &fakeFactstore{tier: tier, status: "APPROVED"}
	server := httptest.NewServer(fake.handler(t))
	store := api.OpenFallbackStore(t.TempDir())
	if _, err := api.CheckGate(mustNewClient(t, server.URL, "tok"), gateRequest(), store, at); err != nil {
		t.Fatalf("warm-up call: %v", err)
	}
	server.Close()
	return store, mustNewClient(t, server.URL, "tok")
}

func TestGateDecidedByFactstoreWhenReachable(t *testing.T) {
	fake := &fakeFactstore{tier: "TIER1", status: "REJECTED"}
	server := httptest.NewServer(fake.handler(t))
	defer server.Close()

	result, err := api.CheckGate(mustNewClient(t, server.URL, "tok"), gateRequest(), api.OpenFallbackStore(t.TempDir()), at)
	if err != nil {
		t.Fatal(err)
	}
	if result.Approved || result.FallbackMode != "NONE" || result.DecidedBy != "server" {
		t.Errorf("got %+v, want a rejection decided by the server", result)
	}
}

func TestStandardTierFailsOpenWhenFactstoreUnreachable(t *testing.T) {
	store, unreachable := warmUp(t, "STANDARD")

	result, err := api.CheckGate(unreachable, gateRequest(), store, at)
	if err != nil {
		t.Fatal(err)
	}
	if !result.Approved || result.FallbackMode != "FAIL_OPEN" || result.DecidedBy != "client" || result.Tier != "STANDARD" {
		t.Errorf("got %+v, want a client fail-open for STANDARD", result)
	}
	if len(store.Pending()) != 1 {
		t.Errorf("expected the fallback to be kept for reporting, got %d pending", len(store.Pending()))
	}
}

func TestTier1FailsClosedWhenFactstoreUnreachable(t *testing.T) {
	store, unreachable := warmUp(t, "TIER1")

	result, err := api.CheckGate(unreachable, gateRequest(), store, at)
	if err != nil {
		t.Fatal(err)
	}
	if result.Approved || result.FallbackMode != "FAIL_CLOSED" {
		t.Errorf("got %+v, want a client fail-closed for TIER1", result)
	}
}

func TestUnknownTierFailsClosed(t *testing.T) {
	server := httptest.NewServer(http.NotFoundHandler())
	server.Close()

	result, err := api.CheckGate(mustNewClient(t, server.URL, "tok"), gateRequest(), api.OpenFallbackStore(t.TempDir()), at)
	if err != nil {
		t.Fatal(err)
	}
	if result.Approved || result.FallbackMode != "FAIL_CLOSED" || result.Tier != "UNKNOWN" {
		t.Errorf("got %+v, want fail-closed with an unknown tier", result)
	}
}

func TestGatewayErrorsCountAsUnreachable(t *testing.T) {
	store, _ := warmUp(t, "STANDARD")
	gateway := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
	}))
	defer gateway.Close()

	result, err := api.CheckGate(mustNewClient(t, gateway.URL, "tok"), gateRequest(), store, at)
	if err != nil {
		t.Fatal(err)
	}
	if result.FallbackMode != "FAIL_OPEN" {
		t.Errorf("a 502 from a proxy means Factstore is unreachable; got %+v", result)
	}
}

func TestAuthErrorsAreNotAnOutage(t *testing.T) {
	store, _ := warmUp(t, "STANDARD")
	forbidden := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusForbidden)
		w.Write([]byte(`{"error":"Forbidden","message":"no"}`))
	}))
	defer forbidden.Close()

	_, err := api.CheckGate(mustNewClient(t, forbidden.URL, "tok"), gateRequest(), store, at)
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) || !apiErr.IsForbidden() {
		t.Fatalf("a refused credential must fail, not fall back; got %v", err)
	}
}

func TestFallbacksAreReportedOnceFactstoreIsBack(t *testing.T) {
	store, unreachable := warmUp(t, "STANDARD")
	first, _ := api.CheckGate(unreachable, gateRequest(), store, at)

	fake := &fakeFactstore{tier: "STANDARD", status: "APPROVED"}
	server := httptest.NewServer(fake.handler(t))
	defer server.Close()
	if _, err := api.CheckGate(mustNewClient(t, server.URL, "tok"), gateRequest(), store, at); err != nil {
		t.Fatal(err)
	}

	if len(fake.reports) != 1 {
		t.Fatalf("expected one fallback report, got %d", len(fake.reports))
	}
	report := fake.reports[0]
	if report["report_id"] != first.ReportID || report["fallback_mode"] != "FAIL_OPEN" || report["tier"] != "STANDARD" ||
		report["target_environment"] != "prod" || report["decided_at"] != "2026-11-12T17:00:00Z" {
		t.Errorf("unexpected report: %v", report)
	}
	if len(store.Pending()) != 0 {
		t.Errorf("a delivered report must not be sent again; %d still pending", len(store.Pending()))
	}
}

func TestFallbackIsDeterministic(t *testing.T) {
	store, unreachable := warmUp(t, "TIER1")
	a, _ := api.CheckGate(unreachable, gateRequest(), store, at)
	b, _ := api.CheckGate(unreachable, gateRequest(), store, at)
	if a.Approved != b.Approved || a.FallbackMode != b.FallbackMode || a.Tier != b.Tier || a.Reason != b.Reason {
		t.Errorf("same inputs gave %+v and %+v", a, b)
	}
}
