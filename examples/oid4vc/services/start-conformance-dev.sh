#!/usr/bin/env bash
#
# Start the IDK services environment for OIDF conformance testing (DEV MODE).
#
# Same as ./start-dev.sh, but layers docker-compose.conformance.yaml on top so Caddy
# also exposes a TLS-terminating :8443 listener with the BCP 195 cipher list the
# OIDF FAPI2 suite's `RequireOnlyBCP195RecommendedCiphersForTLS12` check expects.
# The wallet/RP under test reaches Caddy via an ngrok TLS-passthrough tunnel pointed
# at localhost:8443 (Pay-as-you-go ngrok plan; Personal blocks TLS endpoints).
#
# Regular contributors who don't run the conformance suite should use ./start-dev.sh —
# it keeps Caddy HTTP-only on :8080 and skips ACME / cert provisioning entirely.
#
# Usage:
#   ./start-conformance-dev.sh https://sphereon-oid4vc.ngrok.dev               # default: haip
#   ./start-conformance-dev.sh https://sphereon-oid4vc.ngrok.dev x509-san-dns  # OID4VP plan
#
# Default profile is `haip` because non-HAIP conformance runs are rare — most OIDF
# plans flagged at conformance scope (FAPI2, VCI HAIP, VP HAIP) require the HAIP-
# shaped AS (attest_jwt_client_auth + DPoP required + attestation required). Pass a
# different profile name explicitly if you're running a non-HAIP suite.
#
# Conformance profile values for the second argument:
#   haip               (DEFAULT) HAIP conformance combo (encryption SUPPORTED).
#   haip-enc-required  HAIP combo + credential_response_encryption.encryption_required=true.
#                      Use for OIDF plans that specifically test the required-encryption path;
#                      most plans run with plain `haip` because the suite's
#                      `vci_credential_encryption=encrypted` variant works in either mode.
#   haip-key-attest    HAIP combo + OID4VCI §7.2 key attestations required on EuPid + Mdl.
#                      Lights up `oid4vci-1_0-issuer-fail-invalid-key-attestation-signature`
#                      and the matching happy-path test. Orthogonal to encryption — wallets
#                      can still pick the plain-credential variant; combine with
#                      `haip-enc-required` only when you want both required at once.
#                      Operator step: paste keystores/key-attester/key-attester.jwks.json
#                      into the OIDF plan UI's `vci.key_attestation_jwks` field.
#   default            Plain demo (did:jwk verifier, plain AS); rarely useful here.
#   did-jwk            Explicit did:jwk verifier prefix, plain AS.
#   x509-san-dns       x509_san_dns verifier prefix (OID4VP plan), plain AS.
#   x509-hash          x509_hash verifier prefix (OID4VP plan), plain AS.
#
# Prereqs:
#   1. ngrok TLS tunnel running:
#        ngrok tls --domain=sphereon-oid4vc.ngrok.dev localhost:8443
#   2. The hostname in Caddyfile.conformance matches the ngrok domain you forward to.
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
# Conformance defaults to haip — the only profile most conformance plans accept.
resolve_profile "${2:-haip}"

IDK_ROOT="$(cd ../../.. && pwd)"
IDK_VERSION="$(grep '^version=' "${IDK_ROOT}/gradle.properties" | cut -d= -f2)"
echo "IDK_VERSION=${IDK_VERSION} (from ${IDK_ROOT}/gradle.properties)"
echo "Conformance dev mode: building from local IDK source at ${IDK_ROOT}"

cat > .env <<EOF
EXTERNAL_BASE_URL=${EXTERNAL_BASE_URL}
IDK_VERSION=${IDK_VERSION}
EOF


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
echo "Starting IDK services environment with conformance TLS termination..."
echo "  Profile:           ${PROFILE_NAME}"
if [ "${#COMPOSE_PROFILE_ARGS[@]}" -gt 0 ]; then
    echo "  Layered env files: ${COMPOSE_PROFILE_ARGS[*]}"
fi
echo "  Compose overlay:   docker-compose.conformance.yaml"
echo "  Caddy config:      Caddyfile.conformance (BCP 195 ciphers on :8443)"
echo "  Issuer identifier: ${EXTERNAL_BASE_URL}/oid4vci"
echo "  Issuer metadata:   ${EXTERNAL_BASE_URL}/.well-known/openid-credential-issuer/oid4vci"
echo "  AS issuer:         ${EXTERNAL_BASE_URL}/auth"
echo "  AS discovery:      ${EXTERNAL_BASE_URL}/.well-known/oauth-authorization-server/auth"
echo "  Login form:        ${EXTERNAL_BASE_URL}/auth/login"
echo ""
echo "  ngrok forward:     ngrok tls --domain=<your-domain> localhost:8443"
echo ""

docker compose -f docker-compose.yaml -f docker-compose.conformance.yaml \
    "${COMPOSE_PROFILE_ARGS[@]}" up -d --build

echo ""
echo "Services starting. Check health:"
echo "  curl ${EXTERNAL_BASE_URL}/auth/health"
