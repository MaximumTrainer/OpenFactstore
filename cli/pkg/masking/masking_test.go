package masking

import (
	"bytes"
	"encoding/json"
	"os"
	"reflect"
	"testing"
)

// #238: the CLI masks exactly as the server does (#198), so a fact masked here and then signed
// is accepted unchanged by the server's reject mode.

type goldenFile struct {
	Ruleset string `json:"ruleset"`
	Cases   []struct {
		Name      string          `json:"name"`
		Sensitive []string        `json:"sensitive"`
		Input     json.RawMessage `json:"input"`
		Output    json.RawMessage `json:"output"`
		Findings  []Finding       `json:"findings"`
	} `json:"cases"`
}

func TestSharedGoldenFixtures(t *testing.T) {
	raw, err := os.ReadFile("testdata/golden.json")
	if err != nil {
		t.Fatal(err)
	}
	var golden goldenFile
	if err := json.Unmarshal(raw, &golden); err != nil {
		t.Fatal(err)
	}
	rules := BuiltIn()
	if rules.Version != golden.Ruleset {
		t.Fatalf("ruleset %q, fixtures are for %q", rules.Version, golden.Ruleset)
	}
	for _, c := range golden.Cases {
		t.Run(c.Name, func(t *testing.T) {
			input, err := Parse(c.Input)
			if err != nil {
				t.Fatal(err)
			}
			masked, findings := rules.Scan(input, c.Sensitive)
			out, err := json.Marshal(masked)
			if err != nil {
				t.Fatal(err)
			}
			var got, want any
			json.Unmarshal(out, &got)
			json.Unmarshal(c.Output, &want)
			if !reflect.DeepEqual(got, want) {
				t.Errorf("output\n got: %v\nwant: %v", got, want)
			}
			if c.Findings == nil {
				c.Findings = []Finding{}
			}
			if findings == nil {
				findings = []Finding{}
			}
			if !reflect.DeepEqual(findings, c.Findings) {
				t.Errorf("findings\n got: %v\nwant: %v", findings, c.Findings)
			}
		})
	}
}

func TestRulesAreTheServersRules(t *testing.T) {
	server, err := os.ReadFile("../../../backend/src/main/resources/masking/rules-v1.yaml")
	if err != nil {
		t.Skipf("server rules not available outside the repository: %v", err)
	}
	norm := func(b []byte) []byte { return bytes.ReplaceAll(b, []byte("\r\n"), []byte("\n")) }
	if !bytes.Equal(norm(server), norm(rulesV1)) {
		t.Fatal("cli/pkg/masking/rules-v1.yaml differs from the server's masking/rules-v1.yaml; copy it across")
	}
}
