// Package masking masks secrets and PII in a fact's tool payload before the runner signs it
// (#198, #238). It applies exactly the server's versioned ruleset: rules-v1.yaml here is a copy of
// backend/src/main/resources/masking/rules-v1.yaml, a test fails if they differ, and both
// implementations run the same golden fixtures (testdata/golden.json).
package masking

import (
	"bytes"
	_ "embed"
	"encoding/json"
	"fmt"
	"regexp"
	"strconv"
	"strings"

	"gopkg.in/yaml.v3"
)

//go:embed rules-v1.yaml
var rulesV1 []byte

// Finding names where a value was masked and by which rule; never the value.
type Finding struct {
	Path string `json:"path"`
	Rule string `json:"rule"`
}

type rule struct {
	id         string
	pattern    *regexp.Regexp
	valueGroup int
}

// Ruleset is a versioned set of masking rules.
type Ruleset struct {
	Version string
	rules   []rule
}

const (
	sensitiveRule = "sensitive-field"
	maskedField   = "[MASKED:sensitive-field]"
)

// BuiltIn returns the built-in ruleset, builtin-v1.
func BuiltIn() *Ruleset {
	r, err := Load(rulesV1)
	if err != nil {
		panic(fmt.Sprintf("built-in masking rules: %v", err))
	}
	return r
}

// Load reads a ruleset in the server's rules-v1.yaml format.
func Load(raw []byte) (*Ruleset, error) {
	var doc struct {
		Version string `yaml:"version"`
		Rules   []struct {
			ID      string `yaml:"id"`
			Pattern string `yaml:"pattern"`
		} `yaml:"rules"`
	}
	if err := yaml.Unmarshal(raw, &doc); err != nil {
		return nil, err
	}
	if doc.Version == "" {
		return nil, fmt.Errorf("a masking ruleset needs a version")
	}
	r := &Ruleset{Version: doc.Version}
	for _, d := range doc.Rules {
		re, err := regexp.Compile(d.Pattern)
		if err != nil {
			return nil, fmt.Errorf("rule %s: %w", d.ID, err)
		}
		r.rules = append(r.rules, rule{id: d.ID, pattern: re, valueGroup: re.SubexpIndex("value")})
	}
	return r, nil
}

// Value is a JSON value whose objects keep their key order, so masking reports findings in the
// same order as the server and the body the runner signs is the one it built.
type Value interface{}

// Object is a JSON object with its keys in order.
type Object struct {
	Keys   []string
	Values map[string]Value
}

// MarshalJSON writes the object's keys in order.
func (o *Object) MarshalJSON() ([]byte, error) {
	var b bytes.Buffer
	b.WriteByte('{')
	for i, k := range o.Keys {
		if i > 0 {
			b.WriteByte(',')
		}
		key, _ := json.Marshal(k)
		b.Write(key)
		b.WriteByte(':')
		val, err := json.Marshal(o.Values[k])
		if err != nil {
			return nil, err
		}
		b.Write(val)
	}
	b.WriteByte('}')
	return b.Bytes(), nil
}

// Parse decodes JSON, keeping object key order.
func Parse(raw []byte) (Value, error) {
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.UseNumber()
	v, err := parseValue(dec)
	if err != nil {
		return nil, err
	}
	if dec.More() {
		return nil, fmt.Errorf("unexpected data after the JSON value")
	}
	return v, nil
}

func parseValue(dec *json.Decoder) (Value, error) {
	tok, err := dec.Token()
	if err != nil {
		return nil, err
	}
	delim, isDelim := tok.(json.Delim)
	if !isDelim {
		return tok, nil
	}
	switch delim {
	case '{':
		obj := &Object{Values: map[string]Value{}}
		for dec.More() {
			keyTok, err := dec.Token()
			if err != nil {
				return nil, err
			}
			key := keyTok.(string)
			val, err := parseValue(dec)
			if err != nil {
				return nil, err
			}
			if _, seen := obj.Values[key]; !seen {
				obj.Keys = append(obj.Keys, key)
			}
			obj.Values[key] = val
		}
		_, err := dec.Token()
		return obj, err
	case '[':
		arr := []Value{}
		for dec.More() {
			val, err := parseValue(dec)
			if err != nil {
				return nil, err
			}
			arr = append(arr, val)
		}
		_, err := dec.Token()
		return arr, err
	}
	return nil, fmt.Errorf("unexpected %v", delim)
}

// Scan masks every string value with each rule, and the fields at the sensitive JSON pointers
// ("*" matches any key or index) as a whole. It returns the masked copy and what was masked.
func (r *Ruleset) Scan(v Value, sensitive []string) (Value, []Finding) {
	var patterns [][]string
	for _, p := range sensitive {
		patterns = append(patterns, strings.Split(p, "/")[1:])
	}
	var findings []Finding
	out := r.walk(v, nil, patterns, &findings)
	return out, findings
}

func (r *Ruleset) walk(v Value, path []string, sensitive [][]string, findings *[]Finding) Value {
	switch t := v.(type) {
	case *Object:
		out := &Object{Values: map[string]Value{}}
		for _, k := range t.Keys {
			out.Keys = append(out.Keys, k)
			out.Values[k] = r.walk(t.Values[k], append(append([]string{}, path...), escape(k)), sensitive, findings)
		}
		return out
	case []Value:
		out := make([]Value, len(t))
		for i, item := range t {
			out[i] = r.walk(item, append(append([]string{}, path...), strconv.Itoa(i)), sensitive, findings)
		}
		return out
	}
	if isSensitive(path, sensitive) {
		if v == nil {
			return v
		}
		if s, ok := v.(string); ok && s == maskedField {
			return v
		}
		*findings = append(*findings, Finding{Path: pointer(path), Rule: sensitiveRule})
		return maskedField
	}
	if s, ok := v.(string); ok {
		return r.maskText(s, pointer(path), findings)
	}
	return v
}

func (r *Ruleset) maskText(text, path string, findings *[]Finding) string {
	result := text
	for _, rl := range r.rules {
		matches := rl.pattern.FindAllStringSubmatchIndex(result, -1)
		if len(matches) == 0 {
			continue
		}
		var b strings.Builder
		last := 0
		for _, m := range matches {
			start, end := m[0], m[1]
			if rl.valueGroup > 0 && m[2*rl.valueGroup] >= 0 {
				start, end = m[2*rl.valueGroup], m[2*rl.valueGroup+1]
			}
			b.WriteString(result[last:start])
			b.WriteString("[MASKED:" + rl.id + "]")
			last = end
		}
		b.WriteString(result[last:])
		result = b.String()
		*findings = append(*findings, Finding{Path: path, Rule: rl.id})
	}
	return result
}

func isSensitive(path []string, sensitive [][]string) bool {
	for _, pattern := range sensitive {
		if len(pattern) != len(path) {
			continue
		}
		match := true
		for i := range pattern {
			if pattern[i] != "*" && pattern[i] != path[i] {
				match = false
				break
			}
		}
		if match {
			return true
		}
	}
	return false
}

func pointer(path []string) string {
	if len(path) == 0 {
		return ""
	}
	return "/" + strings.Join(path, "/")
}

func escape(key string) string {
	return strings.ReplaceAll(strings.ReplaceAll(key, "~", "~0"), "/", "~1")
}
