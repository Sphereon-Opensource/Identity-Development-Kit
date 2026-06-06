#!/usr/bin/env bash
#
# Start the IDK services environment for wallet testing (PUBLISHED IMAGES).
#
# Pulls sphereon/idk-* images from Docker Hub and starts them. No Gradle
# build, no IDK source tree required.
#
# For IDK contributors iterating on source, use ./start-dev.sh instead.
#
# Usage:
#   ./start.sh                                          # Auto-detect LAN IP, default profile
#   ./start.sh https://my.ngrok.app                     # Pass URL as argument
#   ./start.sh https://my.ngrok.app haip                # Layer the HAIP conformance profile
#   IDK_VERSION=0.24.0 ./start.sh                       # Pin a specific version
#   EXTERNAL_BASE_URL=http://myhost:8080 ./start.sh     # Override URL via env
#
# Conformance profile values for the second argument:
#   default       Plain demo (did:jwk verifier, plain AS); identical to omitting the arg.
#   did-jwk       Explicit did:jwk verifier prefix, plain AS.
#   x509-san-dns  x509_san_dns verifier prefix (OID4VP plan), plain AS.
#   x509-hash     x509_hash verifier prefix (OID4VP plan), plain AS.
#   haip          HAIP conformance combo: x509_hash verifier + HAIP-shaped AS auth methods.
#
set -euo pipefail
cd "$(dirname "$0")"

SCRIPT_DIR="$(pwd)"
# shellcheck source=lib/resolve-base-url.sh
source "${SCRIPT_DIR}/lib/resolve-base-url.sh"
# shellcheck source=lib/generate-keystores.sh
source "${SCRIPT_DIR}/lib/generate-keystores.sh"
# shellcheck source=lib/resolve-profile.sh
source "${SCRIPT_DIR}/lib/resolve-profile.sh"

resolve_external_base_url "${1:-}"
resolve_profile "${2:-}"

# Version resolution: env var > IDK gradle.properties (if present) > "latest"
if [ -z "${IDK_VERSION:-}" ]; then
    GP="../../../gradle.properties"
    if [ -f "$GP" ]; then
        IDK_VERSION="$(grep '^version=' "$GP" | cut -d= -f2)"
        echo "IDK_VERSION=${IDK_VERSION} (resolved from ${GP})"
    else
        IDK_VERSION=latest
        echo "WARN: IDK source not present and IDK_VERSION not set; using :latest"
    fi
else
    echo "IDK_VERSION=${IDK_VERSION} (from environment)"
fi
export IDK_VERSION

cat > .env <<EOF
EXTERNAL_BASE_URL=${EXTERNAL_BASE_URL}
IDK_VERSION=${IDK_VERSION}
EOF

generate_all_keystores

echo ""
echo "Starting IDK services environment from published images (sphereon/idk-*:${IDK_VERSION})..."
echo "  Profile:           ${PROFILE_NAME}"
if [ "${#COMPOSE_PROFILE_ARGS[@]}" -gt 0 ]; then
    echo "  Layered env files: ${COMPOSE_PROFILE_ARGS[*]}"
fi
echo "  Issuer identifier: ${EXTERNAL_BASE_URL}/oid4vci"
echo "  AS issuer:         ${EXTERNAL_BASE_URL}/auth"
echo ""

docker compose "${COMPOSE_PROFILE_ARGS[@]}" pull
docker compose "${COMPOSE_PROFILE_ARGS[@]}" up -d

echo ""
echo "Services starting. Check health:"
echo "  curl ${EXTERNAL_BASE_URL}/auth/health"
