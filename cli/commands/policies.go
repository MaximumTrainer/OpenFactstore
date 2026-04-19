package commands

import (
"fmt"
"strings"

"github.com/MaximumTrainer/Factstore/cli/internal/output"
"github.com/MaximumTrainer/Factstore/cli/pkg/api"
"github.com/spf13/cobra"
)

var policiesCmd = &cobra.Command{
Use:   "policies",
Short: "Manage compliance policies",
}

var policiesListCmd = &cobra.Command{
Use:   "list",
Short: "List all policies",
RunE: func(cmd *cobra.Command, args []string) error {
c, err := newClient()
if err != nil {
return err
}
policies, err := api.ListPolicies(c)
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(policies)
return nil
}
rows := make([][]string, len(policies))
for i, p := range policies {
rows[i] = []string{p.ID, p.Name, fmt.Sprintf("%v", p.EnforceProvenance), fmt.Sprintf("%v", p.EnforceTrailCompliance), strings.Join(p.RequiredAttestationTypes, ", "), p.CreatedAt}
}
output.PrintTable([]string{"ID", "NAME", "ENFORCE PROVENANCE", "ENFORCE TRAIL", "ATTESTATION TYPES", "CREATED AT"}, rows)
return nil
},
}

var policiesGetCmd = &cobra.Command{
Use:   "get <id>",
Short: "Get a policy by ID",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
c, err := newClient()
if err != nil {
return err
}
p, err := api.GetPolicy(c, args[0])
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(p)
return nil
}
output.PrintTable(
[]string{"FIELD", "VALUE"},
[][]string{
{"ID", p.ID},
{"Name", p.Name},
{"Enforce Provenance", fmt.Sprintf("%v", p.EnforceProvenance)},
{"Enforce Trail Compliance", fmt.Sprintf("%v", p.EnforceTrailCompliance)},
{"Attestation Types", strings.Join(p.RequiredAttestationTypes, ", ")},
{"Org Slug", p.OrgSlug},
{"Created At", p.CreatedAt},
{"Updated At", p.UpdatedAt},
},
)
return nil
},
}

var (
policyCreateName              string
policyCreateEnforceProvenance bool
policyCreateEnforceTrail      bool
policyCreateAttestTypes       string
)

var policiesCreateCmd = &cobra.Command{
Use:   "create",
Short: "Create a new policy",
RunE: func(cmd *cobra.Command, args []string) error {
if policyCreateName == "" {
return fmt.Errorf("--name is required")
}
c, err := newClient()
if err != nil {
return err
}
var types []string
if policyCreateAttestTypes != "" {
for _, t := range strings.Split(policyCreateAttestTypes, ",") {
if s := strings.TrimSpace(t); s != "" {
types = append(types, s)
}
}
}
p, err := api.CreatePolicy(c, api.CreatePolicyRequest{
Name:                     policyCreateName,
EnforceProvenance:        policyCreateEnforceProvenance,
EnforceTrailCompliance:   policyCreateEnforceTrail,
RequiredAttestationTypes: types,
})
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(p)
return nil
}
output.PrintSuccess(fmt.Sprintf("Policy created: %s (%s)", p.Name, p.ID))
return nil
},
}

var policiesDeleteCmd = &cobra.Command{
Use:   "delete <id>",
Short: "Delete a policy by ID",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
c, err := newClient()
if err != nil {
return err
}
if err := api.DeletePolicy(c, args[0]); err != nil {
return err
}
output.PrintSuccess(fmt.Sprintf("Policy %s deleted", args[0]))
return nil
},
}

var (
policyLinkEnvironmentID string
)

var policiesLinkCmd = &cobra.Command{
Use:   "link <policy-id>",
Short: "Attach a policy to an environment",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
if policyLinkEnvironmentID == "" {
return fmt.Errorf("--environment-id is required")
}
c, err := newClient()
if err != nil {
return err
}
attachment, err := api.LinkPolicy(c, api.CreatePolicyAttachmentRequest{
PolicyID:      args[0],
EnvironmentID: policyLinkEnvironmentID,
})
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(attachment)
return nil
}
output.PrintSuccess(fmt.Sprintf("Policy %s linked to environment %s (attachment: %s)", args[0], policyLinkEnvironmentID, attachment.ID))
return nil
},
}

var policiesUnlinkCmd = &cobra.Command{
Use:   "unlink <attachment-id>",
Short: "Remove a policy-environment attachment",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
c, err := newClient()
if err != nil {
return err
}
if err := api.UnlinkPolicy(c, args[0]); err != nil {
return err
}
output.PrintSuccess(fmt.Sprintf("Policy attachment %s removed", args[0]))
return nil
},
}

func init() {
policiesCreateCmd.Flags().StringVar(&policyCreateName, "name", "", "Policy name (required)")
policiesCreateCmd.Flags().BoolVar(&policyCreateEnforceProvenance, "enforce-provenance", false, "Enforce provenance")
policiesCreateCmd.Flags().BoolVar(&policyCreateEnforceTrail, "enforce-trail-compliance", false, "Enforce trail compliance")
policiesCreateCmd.Flags().StringVar(&policyCreateAttestTypes, "attestation-types", "", "Comma-separated required attestation types")

policiesLinkCmd.Flags().StringVar(&policyLinkEnvironmentID, "environment-id", "", "Environment ID to attach policy to (required)")

policiesCmd.AddCommand(policiesListCmd, policiesGetCmd, policiesCreateCmd, policiesDeleteCmd, policiesLinkCmd, policiesUnlinkCmd)
}
