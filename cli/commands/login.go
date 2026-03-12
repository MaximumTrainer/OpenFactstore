package commands

import (
	"fmt"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var loginCmd = &cobra.Command{
	Use:   "login",
	Short: "Verify connectivity to the Factstore API",
	Long:  "Test the configured host and token by listing flows. Prints success or an error.",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		_, err = api.ListFlows(c)
		if err != nil {
			return fmt.Errorf("authentication or connectivity failed: %w", err)
		}
		output.PrintSuccess(fmt.Sprintf("Connected to %s", c.BaseURL))
		return nil
	},
}
