package commands

import (
"fmt"

"github.com/MaximumTrainer/Factstore/cli/internal/output"
"github.com/MaximumTrainer/Factstore/cli/pkg/api"
"github.com/spf13/cobra"
)

var environmentsCmd = &cobra.Command{
Use:   "environments",
Short: "Manage deployment environments",
}

var environmentsListCmd = &cobra.Command{
Use:   "list",
Short: "List all environments",
RunE: func(cmd *cobra.Command, args []string) error {
c, err := newClient()
if err != nil {
return err
}
envs, err := api.ListEnvironments(c)
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(envs)
return nil
}
rows := make([][]string, len(envs))
for i, e := range envs {
rows[i] = []string{e.ID, e.Name, e.Type, e.Description, e.DriftPolicy, e.CreatedAt}
}
output.PrintTable([]string{"ID", "NAME", "TYPE", "DESCRIPTION", "DRIFT POLICY", "CREATED AT"}, rows)
return nil
},
}

var environmentsGetCmd = &cobra.Command{
Use:   "get <id>",
Short: "Get an environment by ID",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
c, err := newClient()
if err != nil {
return err
}
env, err := api.GetEnvironment(c, args[0])
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(env)
return nil
}
output.PrintTable(
[]string{"FIELD", "VALUE"},
[][]string{
{"ID", env.ID},
{"Name", env.Name},
{"Type", env.Type},
{"Description", env.Description},
{"Org Slug", env.OrgSlug},
{"Drift Policy", env.DriftPolicy},
{"Created At", env.CreatedAt},
{"Updated At", env.UpdatedAt},
},
)
return nil
},
}

var (
envCreateName        string
envCreateType        string
envCreateDescription string
)

var environmentsCreateCmd = &cobra.Command{
Use:   "create",
Short: "Create a new environment",
RunE: func(cmd *cobra.Command, args []string) error {
if envCreateName == "" {
return fmt.Errorf("--name is required")
}
if envCreateType == "" {
return fmt.Errorf("--type is required")
}
c, err := newClient()
if err != nil {
return err
}
env, err := api.CreateEnvironment(c, api.CreateEnvironmentRequest{
Name:        envCreateName,
Type:        envCreateType,
Description: envCreateDescription,
})
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(env)
return nil
}
output.PrintSuccess(fmt.Sprintf("Environment created: %s (%s)", env.Name, env.ID))
return nil
},
}

var environmentsDeleteCmd = &cobra.Command{
Use:   "delete <id>",
Short: "Delete an environment by ID",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
c, err := newClient()
if err != nil {
return err
}
if err := api.DeleteEnvironment(c, args[0]); err != nil {
return err
}
output.PrintSuccess(fmt.Sprintf("Environment %s deleted", args[0]))
return nil
},
}

func init() {
environmentsCreateCmd.Flags().StringVar(&envCreateName, "name", "", "Environment name (required)")
environmentsCreateCmd.Flags().StringVar(&envCreateType, "type", "", "Environment type: K8S|ECS|LAMBDA|S3|DOCKER|GENERIC (required)")
environmentsCreateCmd.Flags().StringVar(&envCreateDescription, "description", "", "Environment description")

environmentsCmd.AddCommand(environmentsListCmd, environmentsGetCmd, environmentsCreateCmd, environmentsDeleteCmd)
}
