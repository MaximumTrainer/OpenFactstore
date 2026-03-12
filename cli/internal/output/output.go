package output

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/olekukonko/tablewriter"
)

// PrintTable renders rows in a formatted ASCII table.
func PrintTable(headers []string, rows [][]string) {
	table := tablewriter.NewWriter(os.Stdout)
	table.SetHeader(headers)
	table.SetBorder(true)
	table.SetRowLine(false)
	table.SetHeaderAlignment(tablewriter.ALIGN_LEFT)
	table.SetAlignment(tablewriter.ALIGN_LEFT)
	table.AppendBulk(rows)
	table.Render()
}

// PrintJSON marshals v to pretty-printed JSON and writes it to stdout.
func PrintJSON(v interface{}) {
	data, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		PrintError(fmt.Sprintf("failed to marshal JSON: %v", err))
		return
	}
	fmt.Println(string(data))
}

// PrintError writes a formatted error message to stderr.
func PrintError(msg string) {
	fmt.Fprintf(os.Stderr, "error: %s\n", msg)
}

// PrintSuccess writes a success message to stdout.
func PrintSuccess(msg string) {
	fmt.Printf("✓ %s\n", msg)
}
