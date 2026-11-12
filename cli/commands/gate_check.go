package commands

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var (
	gateCheckArtifact    string
	gateCheckEnvironment string
	gateCheckFlowID      string
	gateCheckRequire     []string
	gateCheckStateDir    string
)

// gateCheckCmd is the deployment gate with risk-based fallback (#193). It exits non-zero when the
// deployment must not proceed, so a pipeline step can use it directly.
var gateCheckCmd = &cobra.Command{
	Use:   "check",
	Short: "Decide whether an artifact may be deployed, falling back by risk tier if Factstore is unreachable",
	Long: `Asks Factstore whether the artifact may be deployed to the environment (POST /api/v1/queries/gates/evaluate).

If Factstore cannot be reached, the CLI decides by the environment's risk tier, remembered from the
last successful call in --state-dir: STANDARD fails open, TIER1 (or a tier never seen) fails closed.
That decision is kept and reported to Factstore on the next successful call, where it is audited.

Exit status: 0 when the deployment may proceed, 1 when it must not.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if gateCheckArtifact == "" || gateCheckEnvironment == "" || gateCheckFlowID == "" {
			return fmt.Errorf("--artifact, --environment and --flow-id are required")
		}
		artifact := gateCheckArtifact
		if !strings.HasPrefix(artifact, "sha256:") {
			artifact = "sha256:" + artifact
		}
		c, err := newClient()
		if err != nil {
			return err
		}
		result, err := api.CheckGate(c, api.GateCheckRequest{
			ArtifactSha256:    artifact,
			TargetEnvironment: gateCheckEnvironment,
			FlowID:            gateCheckFlowID,
			RequiredPolicies:  gateCheckRequire,
		}, api.OpenFallbackStore(gateCheckStateDir), time.Now())
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(result)
		} else {
			decision := "REJECTED"
			if result.Approved {
				decision = "APPROVED"
			}
			output.PrintTable([]string{"FIELD", "VALUE"}, [][]string{
				{"Decision", decision},
				{"Decided by", result.DecidedBy},
				{"Fallback", result.FallbackMode},
				{"Tier", result.Tier},
				{"Reason", result.Reason},
				{"Missing", strings.Join(result.MissingAttestations, ", ")},
			})
		}
		if result.FallbackMode == "FAIL_OPEN" {
			fmt.Fprintln(os.Stderr, "warning: approved WITHOUT evidence because Factstore could not evaluate the gate (fail-open); this is audited")
		}
		if !result.Approved {
			os.Exit(1)
		}
		return nil
	},
}

func defaultGateStateDir() string {
	if home, err := os.UserHomeDir(); err == nil {
		return filepath.Join(home, ".factstore")
	}
	return ".factstore"
}

func init() {
	gateCheckCmd.Flags().StringVar(&gateCheckArtifact, "artifact", "", "SHA-256 digest of the artifact (required)")
	gateCheckCmd.Flags().StringVar(&gateCheckEnvironment, "environment", "", "Target environment name (required)")
	gateCheckCmd.Flags().StringVar(&gateCheckFlowID, "flow-id", "", "Flow whose requirements apply (required)")
	gateCheckCmd.Flags().StringSliceVar(&gateCheckRequire, "require", nil, "Additional attestation types that must have passed")
	gateCheckCmd.Flags().StringVar(&gateCheckStateDir, "state-dir", defaultGateStateDir(),
		"Where tiers and unreported fallbacks are kept; cache it between CI runs")
	gateCmd.AddCommand(gateCheckCmd)
}
