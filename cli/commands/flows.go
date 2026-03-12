package commands

import (
	"fmt"
	"strings"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var flowsCmd = &cobra.Command{
	Use:   "flows",
	Short: "Manage compliance flows",
}

var flowsListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all flows",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		flows, err := api.ListFlows(c)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(flows)
			return nil
		}
		rows := make([][]string, len(flows))
		for i, f := range flows {
			rows[i] = []string{f.ID, f.Name, f.Description, strings.Join(f.RequiredAttestationTypes, ", "), f.CreatedAt}
		}
		output.PrintTable([]string{"ID", "NAME", "DESCRIPTION", "ATTESTATION TYPES", "CREATED AT"}, rows)
		return nil
	},
}

var flowsGetCmd = &cobra.Command{
	Use:   "get <id>",
	Short: "Get a flow by ID",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		flow, err := api.GetFlow(c, args[0])
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(flow)
			return nil
		}
		output.PrintTable(
			[]string{"FIELD", "VALUE"},
			[][]string{
				{"ID", flow.ID},
				{"Name", flow.Name},
				{"Description", flow.Description},
				{"Attestation Types", strings.Join(flow.RequiredAttestationTypes, ", ")},
				{"Created At", flow.CreatedAt},
				{"Updated At", flow.UpdatedAt},
			},
		)
		return nil
	},
}

var (
	flowCreateName         string
	flowCreateDescription  string
	flowCreateAttestTypes  string
)

var flowsCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new flow",
	RunE: func(cmd *cobra.Command, args []string) error {
		if flowCreateName == "" {
			return fmt.Errorf("--name is required")
		}
		c, err := newClient()
		if err != nil {
			return err
		}
		var types []string
		if flowCreateAttestTypes != "" {
			for _, t := range strings.Split(flowCreateAttestTypes, ",") {
				if s := strings.TrimSpace(t); s != "" {
					types = append(types, s)
				}
			}
		}
		flow, err := api.CreateFlow(c, api.CreateFlowRequest{
			Name:                     flowCreateName,
			Description:              flowCreateDescription,
			RequiredAttestationTypes: types,
		})
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(flow)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Flow created: %s (%s)", flow.Name, flow.ID))
		return nil
	},
}

var (
	flowUpdateName        string
	flowUpdateDescription string
	flowUpdateAttestTypes string
)

var flowsUpdateCmd = &cobra.Command{
	Use:   "update <id>",
	Short: "Update an existing flow",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		req := api.UpdateFlowRequest{
			Name:        flowUpdateName,
			Description: flowUpdateDescription,
		}
		if flowUpdateAttestTypes != "" {
			for _, t := range strings.Split(flowUpdateAttestTypes, ",") {
				if s := strings.TrimSpace(t); s != "" {
					req.RequiredAttestationTypes = append(req.RequiredAttestationTypes, s)
				}
			}
		}
		flow, err := api.UpdateFlow(c, args[0], req)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(flow)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Flow updated: %s (%s)", flow.Name, flow.ID))
		return nil
	},
}

var flowsDeleteCmd = &cobra.Command{
	Use:   "delete <id>",
	Short: "Delete a flow by ID",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		if err := api.DeleteFlow(c, args[0]); err != nil {
			return err
		}
		output.PrintSuccess(fmt.Sprintf("Flow %s deleted", args[0]))
		return nil
	},
}

func init() {
	flowsCreateCmd.Flags().StringVar(&flowCreateName, "name", "", "Flow name (required)")
	flowsCreateCmd.Flags().StringVar(&flowCreateDescription, "description", "", "Flow description")
	flowsCreateCmd.Flags().StringVar(&flowCreateAttestTypes, "attestation-types", "", "Comma-separated attestation types")

	flowsUpdateCmd.Flags().StringVar(&flowUpdateName, "name", "", "New flow name")
	flowsUpdateCmd.Flags().StringVar(&flowUpdateDescription, "description", "", "New description")
	flowsUpdateCmd.Flags().StringVar(&flowUpdateAttestTypes, "attestation-types", "", "Comma-separated attestation types")

	flowsCmd.AddCommand(flowsListCmd, flowsGetCmd, flowsCreateCmd, flowsUpdateCmd, flowsDeleteCmd)
}
