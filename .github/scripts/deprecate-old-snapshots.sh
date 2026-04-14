#!/usr/bin/env bash
#
# Deprecate snapshot versions of @sphereon/idk-* packages on npmjs that are
# older than MAX_AGE_DAYS. Idempotent: redeprecating a deprecated version is
# a no-op on npmjs.
#
# Required env:
#   NPM_TOKEN       npmjs auth token with publish rights
#   PACKAGES_FILE   path to a file with one package name per line
#
# Optional env:
#   MAX_AGE_DAYS    snapshots older than this many days are deprecated (default 30)
#   DRY_RUN         set to "true" to print actions without calling npm deprecate
#
set -euo pipefail

MAX_AGE_DAYS="${MAX_AGE_DAYS:-30}"
DRY_RUN="${DRY_RUN:-false}"
PACKAGES_FILE="${PACKAGES_FILE:-packages.txt}"

if [ ! -f "$PACKAGES_FILE" ]; then
  echo "ERROR: package list not found at $PACKAGES_FILE" >&2
  exit 1
fi

CUTOFF_EPOCH=$(date -u -d "${MAX_AGE_DAYS} days ago" +%s)
CUTOFF_ISO=$(date -u -d "@${CUTOFF_EPOCH}" +%Y-%m-%dT%H:%M:%SZ)
echo "Cutoff: snapshots published before ${CUTOFF_ISO} (${MAX_AGE_DAYS}d ago)"
echo "Dry run: ${DRY_RUN}"
echo ""

# Configure npm auth from token. Avoid printing the token.
echo "//registry.npmjs.org/:_authToken=${NPM_TOKEN}" > "${HOME}/.npmrc"

total_deprecated=0
total_skipped=0

while IFS= read -r pkg; do
  # Strip whitespace; skip blank lines
  pkg="$(echo -n "$pkg" | tr -d '[:space:]')"
  [ -z "$pkg" ] && continue
  echo "=== ${pkg} ==="

  # `npm view ... time --json` returns: { "<version>": "<iso8601>", ..., "created": ..., "modified": ... }
  if ! data=$(npm view "$pkg" time --json 2>/dev/null); then
    echo "  not on npmjs, skipping"
    continue
  fi

  # Emit "version<TAB>iso-timestamp" rows for entries whose version key contains -SNAPSHOT
  rows=$(echo "$data" | jq -r '
    to_entries[]
    | select(.key | test("-SNAPSHOT"))
    | "\(.key)\t\(.value)"
  ')

  if [ -z "$rows" ]; then
    echo "  no snapshots published"
    continue
  fi

  while IFS=$'\t' read -r ver ts; do
    [ -z "$ver" ] && continue
    pub_epoch=$(date -u -d "$ts" +%s)
    if [ "$pub_epoch" -lt "$CUTOFF_EPOCH" ]; then
      msg="Old snapshot (>${MAX_AGE_DAYS}d). Use the latest released version of ${pkg}."
      if [ "$DRY_RUN" = "true" ]; then
        echo "  [DRY] would deprecate ${ver} (${ts})"
      else
        echo "  deprecating ${ver} (${ts})"
        if npm deprecate "${pkg}@${ver}" "$msg" >/dev/null 2>&1; then
          total_deprecated=$((total_deprecated + 1))
        else
          echo "  WARN: deprecate failed for ${ver}"
        fi
      fi
    else
      total_skipped=$((total_skipped + 1))
    fi
  done <<< "$rows"
done < "$PACKAGES_FILE"

echo ""
echo "Done. Deprecated: ${total_deprecated}. Within retention: ${total_skipped}."
