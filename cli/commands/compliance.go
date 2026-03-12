package commands

import (
	"fmt"
	"strings"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var complianceCmd = &cobra.Command{
	Use:   "compliance",
	Short: "Compliance and chain-of-custody queries",
}

var complianceArtifactCmd = &cobra.Command{
	Use:   "artifact <sha256-digest>",
	Short: "Get the chain of custody for an artifact",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		coc, err := api.GetChainOfCustody(c, args[0])
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(coc)
			return nil
		}

		fmt.Println("── Artifact ──")
		if coc.Artifact != nil {
			a := coc.Artifact
			output.PrintTable([]string{"FIELD", "VALUE"}, [][]string{
				{"ID", a.ID},
				{"Image", a.ImageName + ":" + a.ImageTag},
				{"Registry", a.Registry},
				{"SHA256", a.Sha256Digest},
				{"Reported By", a.ReportedBy},
				{"Reported At", a.ReportedAt},
			})
		}

		fmt.Println("── Trail ──")
		if coc.Trail != nil {
			t := coc.Trail
			output.PrintTable([]string{"FIELD", "VALUE"}, [][]string{
				{"ID", t.ID},
				{"Commit", t.GitCommitSha},
				{"Branch", t.GitBranch},
				{"Author", t.GitAuthor},
				{"Status", t.Status},
			})
		}

		fmt.Println("── Flow ──")
		if coc.Flow != nil {
			f := coc.Flow
			output.PrintTable([]string{"FIELD", "VALUE"}, [][]string{
				{"ID", f.ID},
				{"Name", f.Name},
				{"Attestation Types", strings.Join(f.RequiredAttestationTypes, ", ")},
			})
		}

		if len(coc.Attestations) > 0 {
			fmt.Println("── Attestations ──")
			rows := make([][]string, len(coc.Attestations))
			for i, a := range coc.Attestations {
				rows[i] = []string{a.ID, a.Type, a.Status, a.EvidenceFileName, a.CreatedAt}
			}
			output.PrintTable([]string{"ID", "TYPE", "STATUS", "EVIDENCE FILE", "CREATED AT"}, rows)
		}
		return nil
	},
}

func init() {
	complianceCmd.AddCommand(complianceArtifactCmd)
}
