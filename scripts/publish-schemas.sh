#!/usr/bin/env bash
# Publish every fact schema in schemas/ to a Factstore registry (#197).
#
# Run from CI after a merge to main. Publishing is idempotent: a version already published with
# the same content answers 200, a new version 201. A 409 means the registry holds a different
# version under the same number, or the change is not backwards compatible; schemaSpecCheck
# should have caught both before the merge.
#
# Usage: FACTSTORE_URL=https://factstore.example.com FACTSTORE_TOKEN=<admin key> scripts/publish-schemas.sh
set -euo pipefail

cd "$(dirname "$0")/.."
: "${FACTSTORE_URL:?set FACTSTORE_URL}"
: "${FACTSTORE_TOKEN:?set FACTSTORE_TOKEN (needs the admin scope)}"

status=0
for file in schemas/*/v*.json; do
  name=$(basename "$(dirname "$file")")
  version=$(basename "$file" .json)
  code=$(curl -sS -o /tmp/publish-schema.out -w '%{http_code}' -X PUT \
    -H "Authorization: Bearer ${FACTSTORE_TOKEN}" -H 'Content-Type: application/json' \
    --data-binary "@${file}" "${FACTSTORE_URL}/api/v1/commands/schemas/${name}/${version}")
  case "$code" in
    200) echo "unchanged  urn:schema:${name}:${version}" ;;
    201) echo "published  urn:schema:${name}:${version}" ;;
    *)   echo "FAILED ($code) urn:schema:${name}:${version}: $(cat /tmp/publish-schema.out)" >&2; status=1 ;;
  esac
done
exit $status
