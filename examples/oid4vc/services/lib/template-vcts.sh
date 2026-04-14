#!/usr/bin/env bash
# Template VCT type metadata with the resolved external URL.
#
# Two safeguards guard against stale vct/resolved/*.json files silently
# surviving a failed substitution run:
#   1. Wipe vct/resolved/ before regenerating.
#   2. Fail-fast if any produced file's top-level vct field isn't the
#      expected HTTPS URL (wallets fetch this per SD-JWT VC section 6.3).
#
# Usage: template_vct_files "$EXTERNAL_BASE_URL" "$VCT_DIR"
#   VCT_DIR defaults to "vct" relative to caller's cwd.

template_vct_files() {
    local base_url="$1"
    local vct_dir="${2:-vct}"

    mkdir -p "${vct_dir}/resolved"
    rm -f "${vct_dir}/resolved/"*.json

    local f name
    for f in "${vct_dir}"/*.json; do
        [ -f "$f" ] || continue
        name=$(basename "$f" .json)
        sed -e "s|\"vct\": \"${name}\"|\"vct\": \"${base_url}/oid4vci/vct/${name}\"|" \
            -e "s|EXTERNAL_BASE_URL|${base_url}|g" \
            "$f" > "${vct_dir}/resolved/$(basename "$f")"
    done

    local vct_errors=0 actual expected
    for f in "${vct_dir}/resolved/"*.json; do
        [ -f "$f" ] || continue
        name=$(basename "$f" .json)
        expected="${base_url}/oid4vci/vct/${name}"
        actual=$(grep -m1 -E '^[[:space:]]*"vct":' "$f" | sed -E 's/.*"vct"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/')
        if [ "$actual" != "$expected" ]; then
            echo "ERROR: ${vct_dir}/resolved/${name}.json has vct='${actual}', expected '${expected}'" >&2
            vct_errors=$((vct_errors + 1))
        fi
    done
    if [ "$vct_errors" -gt 0 ]; then
        echo "Aborting: ${vct_errors} VCT file(s) did not template correctly." >&2
        return 1
    fi
}
