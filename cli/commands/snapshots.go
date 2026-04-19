package commands

import (
"fmt"
"strconv"
"strings"

"github.com/MaximumTrainer/Factstore/cli/internal/output"
"github.com/MaximumTrainer/Factstore/cli/pkg/api"
"github.com/spf13/cobra"
)

var snapshotsCmd = &cobra.Command{
Use:   "snapshots",
Short: "Manage environment snapshots",
}

var snapshotsListCmd = &cobra.Command{
Use:   "list <env-id>",
Short: "List all snapshots for an environment",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
c, err := newClient()
if err != nil {
return err
}
snapshots, err := api.ListSnapshots(c, args[0])
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(snapshots)
return nil
}
rows := make([][]string, len(snapshots))
for i, s := range snapshots {
rows[i] = []string{
s.ID,
fmt.Sprintf("%d", s.SnapshotIndex),
s.RecordedBy,
fmt.Sprintf("%d", len(s.Artifacts)),
s.RecordedAt,
}
}
output.PrintTable([]string{"ID", "INDEX", "RECORDED BY", "ARTIFACTS", "RECORDED AT"}, rows)
return nil
},
}

var snapshotsGetCmd = &cobra.Command{
Use:   "get <env-id> <index>",
Short: "Get a snapshot by environment ID and snapshot index",
Args:  cobra.ExactArgs(2),
RunE: func(cmd *cobra.Command, args []string) error {
c, err := newClient()
if err != nil {
return err
}
snapshot, err := api.GetSnapshot(c, args[0], args[1])
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(snapshot)
return nil
}
output.PrintTable(
[]string{"FIELD", "VALUE"},
[][]string{
{"ID", snapshot.ID},
{"Environment ID", snapshot.EnvironmentID},
{"Snapshot Index", fmt.Sprintf("%d", snapshot.SnapshotIndex)},
{"Recorded By", snapshot.RecordedBy},
{"Recorded At", snapshot.RecordedAt},
{"Artifact Count", fmt.Sprintf("%d", len(snapshot.Artifacts))},
},
)
if len(snapshot.Artifacts) > 0 {
artifactRows := make([][]string, len(snapshot.Artifacts))
for i, a := range snapshot.Artifacts {
artifactRows[i] = []string{a.ArtifactName, a.ArtifactTag, truncate(a.ArtifactSha256, 16), strconv.Itoa(a.InstanceCount)}
}
output.PrintTable([]string{"NAME", "TAG", "SHA256", "INSTANCES"}, artifactRows)
}
return nil
},
}

var (
snapshotRecordedBy string
snapshotArtifacts  []string
)

var snapshotsRecordCmd = &cobra.Command{
Use:   "record <env-id>",
Short: "Record a new snapshot for an environment",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
if snapshotRecordedBy == "" {
return fmt.Errorf("--recorded-by is required")
}
c, err := newClient()
if err != nil {
return err
}
var artifacts []api.SnapshotArtifactItem
for _, raw := range snapshotArtifacts {
parts := strings.SplitN(raw, ":", 4)
if len(parts) < 3 {
return fmt.Errorf("invalid --artifact format %q: expected sha256:name:tag[:count]", raw)
}
count := 1
if len(parts) == 4 {
var parseErr error
count, parseErr = strconv.Atoi(parts[3])
if parseErr != nil {
return fmt.Errorf("invalid instance count in --artifact %q: %w", raw, parseErr)
}
}
artifacts = append(artifacts, api.SnapshotArtifactItem{
ArtifactSha256: parts[0],
ArtifactName:   parts[1],
ArtifactTag:    parts[2],
InstanceCount:  count,
})
}
snapshot, err := api.RecordSnapshot(c, args[0], api.RecordSnapshotRequest{
RecordedBy: snapshotRecordedBy,
Artifacts:  artifacts,
})
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(snapshot)
return nil
}
output.PrintSuccess(fmt.Sprintf("Snapshot recorded: index %d for environment %s", snapshot.SnapshotIndex, snapshot.EnvironmentID))
return nil
},
}

func init() {
snapshotsRecordCmd.Flags().StringVar(&snapshotRecordedBy, "recorded-by", "", "Identity recording the snapshot (required)")
snapshotsRecordCmd.Flags().StringArrayVar(&snapshotArtifacts, "artifact", nil, "Artifact in format sha256:name:tag[:count] (repeatable)")

snapshotsCmd.AddCommand(snapshotsListCmd, snapshotsGetCmd, snapshotsRecordCmd)
}
