package commands

import (
	"fmt"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var trailsCmd = &cobra.Command{
	Use:   "trails",
	Short: "Manage trails",
}

var trailsListFlowID string

var trailsListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all trails",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		trails, err := api.ListTrails(c, trailsListFlowID)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(trails)
			return nil
		}
		rows := make([][]string, len(trails))
		for i, t := range trails {
			rows[i] = []string{t.ID, t.FlowID, t.GitBranch, truncate(t.GitCommitSha, 8), t.GitAuthor, t.Status, t.CreatedAt}
		}
		output.PrintTable([]string{"ID", "FLOW ID", "BRANCH", "COMMIT", "AUTHOR", "STATUS", "CREATED AT"}, rows)
		return nil
	},
}

var trailsGetCmd = &cobra.Command{
	Use:   "get <id>",
	Short: "Get a trail by ID",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		trail, err := api.GetTrail(c, args[0])
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(trail)
			return nil
		}
		output.PrintTable(
			[]string{"FIELD", "VALUE"},
			[][]string{
				{"ID", trail.ID},
				{"Flow ID", trail.FlowID},
				{"Commit SHA", trail.GitCommitSha},
				{"Branch", trail.GitBranch},
				{"Author", trail.GitAuthor},
				{"Author Email", trail.GitAuthorEmail},
				{"PR ID", trail.PullRequestID},
				{"PR Reviewer", trail.PullRequestReviewer},
				{"Deployment Actor", trail.DeploymentActor},
				{"Status", trail.Status},
				{"Created At", trail.CreatedAt},
				{"Updated At", trail.UpdatedAt},
			},
		)
		return nil
	},
}

var (
	trailCreateFlowID      string
	trailCreateCommit      string
	trailCreateBranch      string
	trailCreateAuthor      string
	trailCreateAuthorEmail string
	trailCreatePRID        string
	trailCreatePRReviewer  string
	trailCreateDeployActor string
)

var trailsCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new trail",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		trail, err := api.CreateTrail(c, api.CreateTrailRequest{
			FlowID:              trailCreateFlowID,
			GitCommitSha:        trailCreateCommit,
			GitBranch:           trailCreateBranch,
			GitAuthor:           trailCreateAuthor,
			GitAuthorEmail:      trailCreateAuthorEmail,
			PullRequestID:       trailCreatePRID,
			PullRequestReviewer: trailCreatePRReviewer,
			DeploymentActor:     trailCreateDeployActor,
		})
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(trail)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Trail created: %s", trail.ID))
		return nil
	},
}

// truncate returns s[:n] if len(s) >= n, otherwise s. Safe for empty strings.
func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n]
}

func init() {
	trailsListCmd.Flags().StringVar(&trailsListFlowID, "flow-id", "", "Filter by flow ID")

	trailsCreateCmd.Flags().StringVar(&trailCreateFlowID, "flow-id", "", "Flow ID (required)")
	trailsCreateCmd.Flags().StringVar(&trailCreateCommit, "commit", "", "Git commit SHA (required)")
	trailsCreateCmd.Flags().StringVar(&trailCreateBranch, "branch", "", "Git branch (required)")
	trailsCreateCmd.Flags().StringVar(&trailCreateAuthor, "author", "", "Git author name (required)")
	trailsCreateCmd.Flags().StringVar(&trailCreateAuthorEmail, "author-email", "", "Git author email (required)")
	trailsCreateCmd.Flags().StringVar(&trailCreatePRID, "pr-id", "", "Pull request ID")
	trailsCreateCmd.Flags().StringVar(&trailCreatePRReviewer, "pr-reviewer", "", "Pull request reviewer")
	trailsCreateCmd.Flags().StringVar(&trailCreateDeployActor, "deployment-actor", "", "Deployment actor")
	_ = trailsCreateCmd.MarkFlagRequired("flow-id")
	_ = trailsCreateCmd.MarkFlagRequired("commit")
	_ = trailsCreateCmd.MarkFlagRequired("branch")
	_ = trailsCreateCmd.MarkFlagRequired("author")
	_ = trailsCreateCmd.MarkFlagRequired("author-email")

	trailsCmd.AddCommand(trailsListCmd, trailsGetCmd, trailsCreateCmd)
}
