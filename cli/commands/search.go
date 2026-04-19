package commands

import (
"github.com/MaximumTrainer/Factstore/cli/internal/output"
"github.com/MaximumTrainer/Factstore/cli/pkg/api"
"github.com/spf13/cobra"
)

var searchCmd = &cobra.Command{
Use:   "search <query>",
Short: "Search across trails and artifacts",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
c, err := newClient()
if err != nil {
return err
}
resp, err := api.Search(c, args[0], searchType)
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(resp)
return nil
}
rows := make([][]string, len(resp.Results))
for i, r := range resp.Results {
rows[i] = []string{r.Type, r.ID, r.Description}
}
output.PrintTable([]string{"TYPE", "ID", "DESCRIPTION"}, rows)
return nil
},
}

var searchType string

func init() {
searchCmd.Flags().StringVar(&searchType, "type", "", "Limit results to type: trail|artifact")
}
