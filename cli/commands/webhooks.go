package commands

import (
	"fmt"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var webhooksCmd = &cobra.Command{
	Use:   "webhooks",
	Short: "Manage webhook configurations",
}

var webhooksListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all webhook configurations",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		webhooks, err := api.ListWebhooks(c)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(webhooks)
			return nil
		}
		rows := make([][]string, len(webhooks))
		for i, w := range webhooks {
			active := "false"
			if w.IsActive {
				active = "true"
			}
			rows[i] = []string{w.ID, w.Source, w.FlowID, active, w.CreatedAt}
		}
		output.PrintTable([]string{"ID", "SOURCE", "FLOW ID", "ACTIVE", "CREATED AT"}, rows)
		return nil
	},
}

var (
	webhookCreateSource string
	webhookCreateSecret string
	webhookCreateFlowID string
)

var webhooksCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new webhook configuration",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		webhook, err := api.CreateWebhook(c, api.CreateWebhookRequest{
			Source: webhookCreateSource,
			Secret: webhookCreateSecret,
			FlowID: webhookCreateFlowID,
		})
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(webhook)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Webhook created: %s", webhook.ID))
		return nil
	},
}

var webhooksDeleteCmd = &cobra.Command{
	Use:   "delete <id>",
	Short: "Delete a webhook configuration by ID",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		if err := api.DeleteWebhook(c, args[0]); err != nil {
			return err
		}
		output.PrintSuccess(fmt.Sprintf("Webhook %s deleted", args[0]))
		return nil
	},
}

var webhooksDeliveriesCmd = &cobra.Command{
	Use:   "deliveries <id>",
	Short: "List delivery records for a webhook configuration",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		deliveries, err := api.ListWebhookDeliveries(c, args[0])
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(deliveries)
			return nil
		}
		rows := make([][]string, len(deliveries))
		for i, d := range deliveries {
			rows[i] = []string{d.ID, d.Source, d.EventType, d.Status, d.StatusMessage, d.ReceivedAt}
		}
		output.PrintTable([]string{"ID", "SOURCE", "EVENT TYPE", "STATUS", "MESSAGE", "RECEIVED AT"}, rows)
		return nil
	},
}

func init() {
	webhooksCreateCmd.Flags().StringVar(&webhookCreateSource, "source", "", "Webhook source (required)")
	webhooksCreateCmd.Flags().StringVar(&webhookCreateSecret, "secret", "", "Webhook secret (required)")
	webhooksCreateCmd.Flags().StringVar(&webhookCreateFlowID, "flow-id", "", "Flow ID (required)")
	_ = webhooksCreateCmd.MarkFlagRequired("source")
	_ = webhooksCreateCmd.MarkFlagRequired("secret")
	_ = webhooksCreateCmd.MarkFlagRequired("flow-id")

	webhooksCmd.AddCommand(webhooksListCmd, webhooksCreateCmd, webhooksDeleteCmd, webhooksDeliveriesCmd)
}
