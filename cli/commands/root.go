package commands

import (
	"fmt"
	"os"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
	"github.com/MaximumTrainer/Factstore/cli/internal/config"
	"github.com/spf13/cobra"
)

var (
	cfgHost      string
	cfgQueryHost string
	cfgToken     string
	jsonOutput   bool
)

// RootCmd is the top-level command.
var RootCmd = &cobra.Command{
	Use:   "factstore",
	Short: "Factstore CLI — interact with the Supply Chain Compliance Fact Store",
	Long: `factstore is a command-line tool for managing flows, trails, artifacts,
attestations and compliance assertions in the Factstore API.`,
}

func init() {
	RootCmd.PersistentFlags().StringVar(&cfgHost, "host", "", "API host (overrides config/FACTSTORE_HOST)")
	RootCmd.PersistentFlags().StringVar(&cfgQueryHost, "query-host", "", "Query API host for read operations (overrides config/FACTSTORE_QUERY_HOST)")
	RootCmd.PersistentFlags().StringVar(&cfgToken, "token", "", "Bearer token (overrides config/FACTSTORE_TOKEN)")
	RootCmd.PersistentFlags().BoolVar(&jsonOutput, "json", false, "Output as JSON")

	RootCmd.AddCommand(configureCmd)
	RootCmd.AddCommand(loginCmd)
	RootCmd.AddCommand(flowsCmd)
	RootCmd.AddCommand(trailsCmd)
	RootCmd.AddCommand(artifactsCmd)
	RootCmd.AddCommand(attestationsCmd)
	RootCmd.AddCommand(assertCmd)
	RootCmd.AddCommand(complianceCmd)
	RootCmd.AddCommand(webhooksCmd)
	RootCmd.AddCommand(gateCmd)
}

// newClient builds an HTTP client from merged config + flag overrides.
// When a query-host is configured, GET requests are routed to the query
// (read) service while mutating requests go to the command (write) service.
func newClient() (*client.Client, error) {
	cfg, err := config.Load()
	if err != nil {
		return nil, fmt.Errorf("load config: %w", err)
	}

	host := cfg.Host
	if cfgHost != "" {
		host = cfgHost
	}
	queryHost := cfg.QueryHost
	if cfgQueryHost != "" {
		queryHost = cfgQueryHost
	}
	token := cfg.Token
	if cfgToken != "" {
		token = cfgToken
	}

	if host == "" {
		fmt.Fprintln(os.Stderr, "hint: run 'factstore configure' or set FACTSTORE_HOST to set the API host")
		return nil, fmt.Errorf("no host configured")
	}
	return client.NewWithQueryHost(host, queryHost, token)
}
