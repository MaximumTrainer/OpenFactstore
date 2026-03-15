package commands

import (
	"fmt"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var gateCmd = &cobra.Command{
	Use:   "gate",
	Short: "Manage deployment gates",
}

var (
	gateEvalArtifact    string
	gateEvalEnvironment string
	gateEvalFlowID      string
)

var gateEvaluateCmd = &cobra.Command{
	Use:   "evaluate",
	Short: "Evaluate a deployment gate for an artifact",
	Long:  "Check whether an artifact is allowed to be deployed by evaluating gate policies.",
	RunE: func(cmd *cobra.Command, args []string) error {
		if gateEvalArtifact == "" {
			return fmt.Errorf("--artifact is required")
		}
		c, err := newClient()
		if err != nil {
			return err
		}
		result, err := api.EvaluateGate(c, api.GateEvaluateRequest{
			ArtifactSha256: gateEvalArtifact,
			Environment:    gateEvalEnvironment,
			FlowID:         gateEvalFlowID,
		})
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(result)
			return nil
		}
		allowed := "false"
		if result.Allowed {
			allowed = "true"
		}
		output.PrintTable(
			[]string{"FIELD", "VALUE"},
			[][]string{
				{"Artifact SHA256", gateEvalArtifact},
				{"Environment", gateEvalEnvironment},
				{"Allowed", allowed},
				{"Reason", result.Reason},
				{"Policy Name", result.PolicyName},
			},
		)
		return nil
	},
}

var (
	gateListEnvironment string
	gateListLimit       int
)

var gateListCmd = &cobra.Command{
	Use:   "list",
	Short: "List recent gate evaluation results",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		results, err := api.ListGateResults(c, gateListEnvironment, gateListLimit)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(results)
			return nil
		}
		rows := make([][]string, len(results))
		for i, r := range results {
			allowed := "false"
			if r.Allowed {
				allowed = "true"
			}
			rows[i] = []string{r.ID, truncate(r.ArtifactSha256, 16), r.Environment, allowed, r.PolicyName, truncate(r.Reason, 40), r.EvaluatedAt}
		}
		output.PrintTable([]string{"ID", "ARTIFACT", "ENVIRONMENT", "ALLOWED", "POLICY", "REASON", "EVALUATED AT"}, rows)
		return nil
	},
}

func init() {
	gateEvaluateCmd.Flags().StringVar(&gateEvalArtifact, "artifact", "", "SHA-256 digest of the artifact (required)")
	gateEvaluateCmd.Flags().StringVar(&gateEvalEnvironment, "environment", "", "Target environment name")
	gateEvaluateCmd.Flags().StringVar(&gateEvalFlowID, "flow-id", "", "Flow ID for policy lookup")

	gateListCmd.Flags().StringVar(&gateListEnvironment, "environment", "", "Filter by environment name")
	gateListCmd.Flags().IntVar(&gateListLimit, "limit", 20, "Maximum number of results to return")

	gateCmd.AddCommand(gateEvaluateCmd, gateListCmd)
}
