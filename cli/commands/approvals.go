package commands

import (
"fmt"
"strings"

"github.com/MaximumTrainer/Factstore/cli/internal/output"
"github.com/MaximumTrainer/Factstore/cli/pkg/api"
"github.com/spf13/cobra"
)

var approvalsCmd = &cobra.Command{
Use:   "approvals",
Short: "Manage approval requests",
}

var (
approvalsListTrailID string
approvalsListStatus  string
)

var approvalsListCmd = &cobra.Command{
Use:   "list",
Short: "List approvals (by trail ID or status filter)",
RunE: func(cmd *cobra.Command, args []string) error {
c, err := newClient()
if err != nil {
return err
}
var approvals []api.ApprovalResponse
if approvalsListTrailID != "" {
approvals, err = api.ListApprovalsByTrail(c, approvalsListTrailID)
} else {
approvals, err = api.ListApprovals(c, approvalsListStatus)
}
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(approvals)
return nil
}
rows := make([][]string, len(approvals))
for i, a := range approvals {
rows[i] = []string{a.ID, a.TrailID, a.Status, a.RequestedAt}
}
output.PrintTable([]string{"ID", "TRAIL ID", "STATUS", "REQUESTED AT"}, rows)
return nil
},
}

var approvalsGetCmd = &cobra.Command{
Use:   "get <id>",
Short: "Get an approval by ID",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
c, err := newClient()
if err != nil {
return err
}
a, err := api.GetApproval(c, args[0])
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(a)
return nil
}
output.PrintTable(
[]string{"FIELD", "VALUE"},
[][]string{
{"ID", a.ID},
{"Trail ID", a.TrailID},
{"Flow ID", a.FlowID},
{"Status", a.Status},
{"Required Approvers", strings.Join(a.RequiredApprovers, ", ")},
{"Comments", a.Comments},
{"Requested At", a.RequestedAt},
{"Deadline", a.Deadline},
{"Resolved At", a.ResolvedAt},
},
)
return nil
},
}

var (
approvalRequestTrailID  string
approvalRequestApprovers string
approvalRequestComments  string
)

var approvalsRequestCmd = &cobra.Command{
Use:   "request",
Short: "Request approval for a trail",
RunE: func(cmd *cobra.Command, args []string) error {
if approvalRequestTrailID == "" {
return fmt.Errorf("--trail-id is required")
}
c, err := newClient()
if err != nil {
return err
}
var approvers []string
if approvalRequestApprovers != "" {
for _, a := range strings.Split(approvalRequestApprovers, ",") {
if s := strings.TrimSpace(a); s != "" {
approvers = append(approvers, s)
}
}
}
approval, err := api.RequestApproval(c, approvalRequestTrailID, api.CreateApprovalRequest{
TrailID:           approvalRequestTrailID,
RequiredApprovers: approvers,
Comments:          approvalRequestComments,
})
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(approval)
return nil
}
output.PrintSuccess(fmt.Sprintf("Approval requested: %s (status: %s)", approval.ID, approval.Status))
return nil
},
}

var (
approvalApproveApprover string
approvalApproveComments string
)

var approvalsApproveCmd = &cobra.Command{
Use:   "approve <id>",
Short: "Approve an approval request",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
if approvalApproveApprover == "" {
return fmt.Errorf("--approver is required")
}
c, err := newClient()
if err != nil {
return err
}
approval, err := api.ApproveApproval(c, args[0], api.ApproveRequest{
ApproverIdentity: approvalApproveApprover,
Comments:         approvalApproveComments,
})
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(approval)
return nil
}
output.PrintSuccess(fmt.Sprintf("Approval %s approved (status: %s)", args[0], approval.Status))
return nil
},
}

var (
approvalRejectApprover string
approvalRejectComments string
)

var approvalsRejectCmd = &cobra.Command{
Use:   "reject <id>",
Short: "Reject an approval request",
Args:  cobra.ExactArgs(1),
RunE: func(cmd *cobra.Command, args []string) error {
if approvalRejectApprover == "" {
return fmt.Errorf("--approver is required")
}
c, err := newClient()
if err != nil {
return err
}
approval, err := api.RejectApproval(c, args[0], api.RejectRequest{
ApproverIdentity: approvalRejectApprover,
Comments:         approvalRejectComments,
})
if err != nil {
return err
}
if jsonOutput {
output.PrintJSON(approval)
return nil
}
output.PrintSuccess(fmt.Sprintf("Approval %s rejected (status: %s)", args[0], approval.Status))
return nil
},
}

func init() {
approvalsListCmd.Flags().StringVar(&approvalsListTrailID, "trail-id", "", "Filter by trail ID")
approvalsListCmd.Flags().StringVar(&approvalsListStatus, "status", "", "Filter by status: PENDING_APPROVAL|APPROVED|REJECTED|EXPIRED")

approvalsRequestCmd.Flags().StringVar(&approvalRequestTrailID, "trail-id", "", "Trail ID to request approval for (required)")
approvalsRequestCmd.Flags().StringVar(&approvalRequestApprovers, "approvers", "", "Comma-separated approver identities")
approvalsRequestCmd.Flags().StringVar(&approvalRequestComments, "comments", "", "Optional comments")

approvalsApproveCmd.Flags().StringVar(&approvalApproveApprover, "approver", "", "Approver identity (required)")
approvalsApproveCmd.Flags().StringVar(&approvalApproveComments, "comments", "", "Optional comments")

approvalsRejectCmd.Flags().StringVar(&approvalRejectApprover, "approver", "", "Approver identity (required)")
approvalsRejectCmd.Flags().StringVar(&approvalRejectComments, "comments", "", "Optional comments")

approvalsCmd.AddCommand(approvalsListCmd, approvalsGetCmd, approvalsRequestCmd, approvalsApproveCmd, approvalsRejectCmd)
}
