#!/usr/bin/env bash
#
# Start the IDK services environment for wallet testing (DEV MODE).
#
# Dev mode: builds fat JARs from the local IDK source tree, then builds and
# starts Docker images from those JARs. Use this when iterating on IDK code.
#
# For end-users who just want to run published images from Docker Hub, use
# ./start.sh instead.
#
# Usage:
#   ./start-dev.sh                                          # Auto-detect LAN IP, default profile
#   ./start-dev.sh https://my.ngrok.app                     # Pass URL as argument
#   ./start-dev.sh https://my.ngrok.app haip                # Layer the HAIP conformance profile
#   EXTERNAL_BASE_URL=http://myhost:8080 ./start-dev.sh     # Use env var
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
# shellcheck source=lib/template-vcts.sh
source "${SCRIPT_DIR}/lib/template-vcts.sh"
# shellcheck source=lib/generate-keystores.sh
source "${SCRIPT_DIR}/lib/generate-keystores.sh"
# shellcheck source=lib/resolve-profile.sh
source "${SCRIPT_DIR}/lib/resolve-profile.sh"

resolve_external_base_url "${1:-}"
resolve_profile "${2:-}"

IDK_ROOT="$(cd ../../.. && pwd)"
IDK_VERSION="$(grep '^version=' "${IDK_ROOT}/gradle.properties" | cut -d= -f2)"
echo "IDK_VERSION=${IDK_VERSION} (from ${IDK_ROOT}/gradle.properties)"
echo "Dev mode: building from local IDK source at ${IDK_ROOT}"

cat > .env <<EOF
EXTERNAL_BASE_URL=${EXTERNAL_BASE_URL}
IDK_VERSION=${IDK_VERSION}
EOF

template_vct_files "${EXTERNAL_BASE_URL}"

echo "Building fat JARs from ${IDK_ROOT}..."
(cd "${IDK_ROOT}" && ./gradlew \
    :services-oauth2-as-rest:buildFatJar \
    :services-oid4vci-issuer-rest:buildFatJar \
    :services-oid4vp-verifier-rest:buildFatJar \
    :examples-oid4vc-webapp-server:buildFatJar \
    --no-daemon --parallel \
    -Dkotlin.mpp.enabledTargets=jvm \
    -Dkotlin.native.ignoreDisabledTargets=true)

mkdir -p jars
cp "${IDK_ROOT}/services/oauth2-as/rest/build/libs/"*-all.jar jars/oauth2-as.jar
cp "${IDK_ROOT}/services/oid4vci-issuer/rest/build/libs/"*-all.jar jars/oid4vci-issuer.jar
cp "${IDK_ROOT}/services/oid4vp-verifier/rest/build/libs/"*-all.jar jars/oid4vp-verifier.jar
cp "${IDK_ROOT}/examples/oid4vc/webapp/server/build/libs/"*-all.jar jars/webapp.jar

generate_all_keystores

echo ""
echo "Starting IDK services environment..."
echo "  Profile:           ${PROFILE_NAME}"
if [ "${#COMPOSE_PROFILE_ARGS[@]}" -gt 0 ]; then
    echo "  Layered env files: ${COMPOSE_PROFILE_ARGS[*]}"
fi
echo "  Issuer identifier: ${EXTERNAL_BASE_URL}/oid4vci"
echo "  Issuer metadata:   ${EXTERNAL_BASE_URL}/.well-known/openid-credential-issuer/oid4vci"
echo "  AS issuer:         ${EXTERNAL_BASE_URL}/auth"
echo "  AS discovery:      ${EXTERNAL_BASE_URL}/.well-known/oauth-authorization-server/auth"
echo "  Login form:        ${EXTERNAL_BASE_URL}/auth/login"
echo ""

docker compose "${COMPOSE_PROFILE_ARGS[@]}" up -d --build

echo ""
echo "Services starting. Check health:"
echo "  curl ${EXTERNAL_BASE_URL}/auth/health"
