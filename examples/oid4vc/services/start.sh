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
#   ./start.sh                                          # Auto-detect LAN IP, version from IDK gradle.properties or :latest
#   ./start.sh https://my.ngrok.app                     # Pass URL as argument
#   IDK_VERSION=0.24.0 ./start.sh                       # Pin a specific version
#   EXTERNAL_BASE_URL=http://myhost:8080 ./start.sh     # Override URL via env
#
set -euo pipefail
cd "$(dirname "$0")"

SCRIPT_DIR="$(pwd)"
# shellcheck source=lib/resolve-base-url.sh
source "${SCRIPT_DIR}/lib/resolve-base-url.sh"
# shellcheck source=lib/template-vcts.sh
source "${SCRIPT_DIR}/lib/template-vcts.sh"
# shellcheck source=lib/generate-keystores.sh
source "${SCRIPT_DIR}/lib/generate-keystores.sh"

resolve_external_base_url "${1:-}"

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

template_vct_files "${EXTERNAL_BASE_URL}"
generate_all_keystores

echo ""
echo "Starting IDK services environment from published images (sphereon/idk-*:${IDK_VERSION})..."
echo "  Issuer identifier: ${EXTERNAL_BASE_URL}/oid4vci"
echo "  AS issuer:         ${EXTERNAL_BASE_URL}/auth"
echo ""

docker compose pull
docker compose up -d

echo ""
echo "Services starting. Check health:"
echo "  curl ${EXTERNAL_BASE_URL}/auth/health"
