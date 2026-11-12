package commands

import (
	"crypto/rand"
	"fmt"
	"io"
	"os"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var factCmd = &cobra.Command{
	Use:   "fact",
	Short: "Send facts to Factstore",
}

var (
	factToolFile   string
	factSchemaRef  string
	factSha256     string
	factRepository string
	factID         string
	factKeyFile    string
	factKeyID      string
)

var factPushCmd = &cobra.Command{
	Use:   "push",
	Short: "Mask, sign and send a fact",
	Long: `Builds a fact from a tool payload, masks secrets and PII with Factstore's own ruleset (and any
fields its schema marks x-sensitive), signs the exact bytes with the runner's Ed25519 key, and sends
it to POST /api/v1/commands/facts. Prints the receipt.

The signing key is a PKCS#8 PEM file (openssl genpkey -algorithm ed25519) registered in Factstore
under --key-id. Both can come from FACTSTORE_SIGNING_KEY_FILE and FACTSTORE_SIGNING_KEY_ID.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if factToolFile == "" || factSchemaRef == "" || factSha256 == "" {
			return fmt.Errorf("--tool, --schema-ref and --sha256 are required")
		}
		keyFile := firstNonEmpty(factKeyFile, os.Getenv("FACTSTORE_SIGNING_KEY_FILE"))
		keyID := firstNonEmpty(factKeyID, os.Getenv("FACTSTORE_SIGNING_KEY_ID"))
		if keyFile == "" || keyID == "" {
			return fmt.Errorf("a signing key is required: --signing-key and --key-id (or FACTSTORE_SIGNING_KEY_FILE and FACTSTORE_SIGNING_KEY_ID)")
		}
		key, err := api.LoadSigningKeyFile(keyFile, keyID)
		if err != nil {
			return err
		}
		tool, err := readToolPayload(factToolFile)
		if err != nil {
			return err
		}
		id := factID
		if id == "" {
			id = randomUUID()
		}
		c, err := newClient()
		if err != nil {
			return err
		}
		receipt, err := api.PushFact(c, api.FactPushRequest{
			FactID: id, Sha256: factSha256, Repository: factRepository, SchemaRef: factSchemaRef, Tool: tool,
		}, key)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(receipt)
			return nil
		}
		output.PrintTable([]string{"FIELD", "VALUE"}, [][]string{
			{"Fact", receipt.FactID},
			{"Schema", receipt.SchemaRef},
			{"Receipt hash", receipt.ReceiptHash},
			{"Ledger index", fmt.Sprint(receipt.LedgerIndex)},
			{"Ingested at", receipt.IngestedAt},
		})
		return nil
	},
}

func readToolPayload(path string) ([]byte, error) {
	if path == "-" {
		return io.ReadAll(os.Stdin)
	}
	return os.ReadFile(path)
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if v != "" {
			return v
		}
	}
	return ""
}

func randomUUID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

func init() {
	factPushCmd.Flags().StringVar(&factToolFile, "tool", "", "File with the tool payload as JSON, or - for stdin (required)")
	factPushCmd.Flags().StringVar(&factSchemaRef, "schema-ref", "", "Schema the payload conforms to, urn:schema:<name>:v<n> (required)")
	factPushCmd.Flags().StringVar(&factSha256, "sha256", "", "Digest of the subject artifact (required)")
	factPushCmd.Flags().StringVar(&factRepository, "repository", "", "Repository the fact is about, e.g. org/service")
	factPushCmd.Flags().StringVar(&factID, "fact-id", "", "Fact id (a UUID); generated when omitted")
	factPushCmd.Flags().StringVar(&factKeyFile, "signing-key", "", "Ed25519 private key, PKCS#8 PEM file")
	factPushCmd.Flags().StringVar(&factKeyID, "key-id", "", "Id the signing key is registered under in Factstore")
	factCmd.AddCommand(factPushCmd)
	RootCmd.AddCommand(factCmd)
}
