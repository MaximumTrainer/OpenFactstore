package commands

import (
	"fmt"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var attestationsCmd = &cobra.Command{
	Use:   "attestations",
	Short: "Manage attestations",
}

var attestationsListTrailID string

var attestationsListCmd = &cobra.Command{
	Use:   "list",
	Short: "List attestations for a trail",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		attestations, err := api.ListAttestations(c, attestationsListTrailID)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(attestations)
			return nil
		}
		rows := make([][]string, len(attestations))
		for i, a := range attestations {
			rows[i] = []string{a.ID, a.Type, a.Status, a.EvidenceFileName, a.CreatedAt}
		}
		output.PrintTable([]string{"ID", "TYPE", "STATUS", "EVIDENCE FILE", "CREATED AT"}, rows)
		return nil
	},
}

var (
	attestationCreateTrailID string
	attestationCreateType    string
	attestationCreateStatus  string
	attestationCreateDetails string
)

var attestationsCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new attestation on a trail",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		result, err := api.CreateAttestation(c, attestationCreateTrailID, api.CreateAttestationRequest{
			Type:    attestationCreateType,
			Status:  attestationCreateStatus,
			Details: attestationCreateDetails,
		})
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(result)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Attestation created: %s", result.ID))
		return nil
	},
}

var (
	evidenceTrailID       string
	evidenceAttestationID string
)

var attestationsUploadEvidenceCmd = &cobra.Command{
	Use:   "upload-evidence <file-path>",
	Short: "Upload evidence file for an attestation",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		result, err := api.UploadEvidence(c, evidenceTrailID, evidenceAttestationID, args[0])
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(result)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Evidence uploaded for attestation %s", result.ID))
		return nil
	},
}

func init() {
	attestationsListCmd.Flags().StringVar(&attestationsListTrailID, "trail-id", "", "Trail ID (required)")
	_ = attestationsListCmd.MarkFlagRequired("trail-id")

	attestationsCreateCmd.Flags().StringVar(&attestationCreateTrailID, "trail-id", "", "Trail ID (required)")
	attestationsCreateCmd.Flags().StringVar(&attestationCreateType, "type", "", "Attestation type (required)")
	attestationsCreateCmd.Flags().StringVar(&attestationCreateStatus, "status", "", "Attestation status (required)")
	attestationsCreateCmd.Flags().StringVar(&attestationCreateDetails, "details", "", "Additional details")
	_ = attestationsCreateCmd.MarkFlagRequired("trail-id")
	_ = attestationsCreateCmd.MarkFlagRequired("type")
	_ = attestationsCreateCmd.MarkFlagRequired("status")

	attestationsUploadEvidenceCmd.Flags().StringVar(&evidenceTrailID, "trail-id", "", "Trail ID (required)")
	attestationsUploadEvidenceCmd.Flags().StringVar(&evidenceAttestationID, "attestation-id", "", "Attestation ID (required)")
	_ = attestationsUploadEvidenceCmd.MarkFlagRequired("trail-id")
	_ = attestationsUploadEvidenceCmd.MarkFlagRequired("attestation-id")

	attestationsCmd.AddCommand(attestationsListCmd, attestationsCreateCmd, attestationsUploadEvidenceCmd)
}
