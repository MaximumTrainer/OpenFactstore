package commands

import (
"fmt"

"github.com/MaximumTrainer/Factstore/cli/internal/output"
"github.com/MaximumTrainer/Factstore/cli/pkg/api"
"github.com/spf13/cobra"
)

var logCmd = &cobra.Command{
Use:   "log",
Short: "View event logs",
}

var logEnvironmentCmd = &cobra.Command{
Use:   "environment <env-id>",
Short: "Show a changelog of artifact events from consecutive snapshots",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
envID := args[0]
c, err := newClient()
if err != nil {
return err
}
snapshots, err := api.ListSnapshots(c, envID)
if err != nil {
return err
}
if len(snapshots) < 2 {
fmt.Println("No consecutive snapshots to diff.")
return nil
}

var rows [][]string
for i := 0; i < len(snapshots)-1; i++ {
from := snapshots[i].SnapshotIndex
to := snapshots[i+1].SnapshotIndex
diff, diffErr := api.DiffSnapshots(c, envID, from, to)
if diffErr != nil {
return diffErr
}
snapshotLabel := fmt.Sprintf("%d→%d", from, to)
for _, a := range diff.Added {
rows = append(rows, []string{
snapshotLabel,
fmt.Sprintf("%d instance(s) started running", 1),
fmt.Sprintf("%s:%s", a.ArtifactName, a.ArtifactTag),
truncate(a.Sha256To, 16),
})
}
for _, r := range diff.Removed {
rows = append(rows, []string{
snapshotLabel,
"no longer running",
fmt.Sprintf("%s:%s", r.ArtifactName, r.ArtifactTag),
truncate(r.Sha256From, 16),
})
}
for _, u := range diff.Updated {
rows = append(rows, []string{
snapshotLabel,
"updated",
fmt.Sprintf("%s:%s", u.ArtifactName, u.ArtifactTag),
truncate(u.Sha256To, 16),
})
}
}

if jsonOutput {
output.PrintJSON(rows)
return nil
}
if len(rows) == 0 {
fmt.Println("No changes detected across snapshots.")
return nil
}
output.PrintTable([]string{"SNAPSHOT", "EVENT", "ARTIFACT", "SHA256"}, rows)
return nil
},
}

func init() {
logCmd.AddCommand(logEnvironmentCmd)
}
