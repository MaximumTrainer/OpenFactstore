package commands

import (
	"fmt"
	"strings"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var (
	assertSha256 string
	assertFlowID string
)

var assertCmd = &cobra.Command{
	Use:   "assert",
	Short: "Assert compliance for an artifact against a flow",
	Long:  "Check whether the artifact identified by SHA-256 meets all attestation requirements of the given flow.",
	RunE: func(cmd *cobra.Command, args []string) error {
		if assertSha256 == "" {
			return fmt.Errorf("--sha256 is required")
		}
		if assertFlowID == "" {
			return fmt.Errorf("--flow-id is required")
		}
		c, err := newClient()
		if err != nil {
			return err
		}
		result, err := api.Assert(c, api.AssertRequest{
			Sha256Digest: assertSha256,
			FlowID:       assertFlowID,
		})
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(result)
			return nil
		}
		output.PrintTable(
			[]string{"FIELD", "VALUE"},
			[][]string{
				{"SHA256", result.Sha256Digest},
				{"Flow ID", result.FlowID},
				{"Status", result.Status},
				{"Missing Attestations", strings.Join(result.MissingAttestationTypes, ", ")},
				{"Failed Attestations", strings.Join(result.FailedAttestationTypes, ", ")},
				{"Details", result.Details},
			},
		)
		return nil
	},
}

func init() {
	assertCmd.Flags().StringVar(&assertSha256, "sha256", "", "SHA-256 digest of the artifact (required)")
	assertCmd.Flags().StringVar(&assertFlowID, "flow-id", "", "Flow ID to assert against (required)")
}
