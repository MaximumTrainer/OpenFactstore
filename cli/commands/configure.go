package commands

import (
	"bufio"
	"fmt"
	"os"
	"strings"

	"github.com/MaximumTrainer/Factstore/cli/internal/config"
	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/spf13/cobra"
	"golang.org/x/term"
)

var configureCmd = &cobra.Command{
	Use:   "configure",
	Short: "Set the API host, query host, and authentication token",
	Long:  "Interactively set the Factstore API host, query host, and bearer token, saved to ~/.factstore.yaml.",
	RunE: func(cmd *cobra.Command, args []string) error {
		reader := bufio.NewReader(os.Stdin)

		fmt.Print("API host (e.g. https://api.factstore.example.com): ")
		host, err := reader.ReadString('\n')
		if err != nil {
			return fmt.Errorf("read host: %w", err)
		}
		host = strings.TrimSpace(host)

		fmt.Print("Query host (leave blank to use API host for reads): ")
		queryHost, err := reader.ReadString('\n')
		if err != nil {
			return fmt.Errorf("read query host: %w", err)
		}
		queryHost = strings.TrimSpace(queryHost)

		fmt.Print("Bearer token: ")
		tokenBytes, err := term.ReadPassword(int(os.Stdin.Fd()))
		fmt.Println() // newline after the hidden input
		if err != nil {
			return fmt.Errorf("read token: %w", err)
		}
		token := strings.TrimSpace(string(tokenBytes))

		if err := config.Save(host, token, queryHost); err != nil {
			return fmt.Errorf("save config: %w", err)
		}
		output.PrintSuccess("Configuration saved to ~/.factstore.yaml")
		return nil
	},
}
