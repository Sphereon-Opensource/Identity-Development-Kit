#!/usr/bin/env bash
#
# Start the IDK services environment for wallet testing.
#
# Usage:
#   ./start.sh                                          # Auto-detect LAN IP
#   ./start.sh https://my.ngrok.app                     # Pass URL as argument
#   EXTERNAL_BASE_URL=http://myhost:8080 ./start.sh     # Use env var
#
set -euo pipefail
cd "$(dirname "$0")"

# Accept EXTERNAL_BASE_URL as first argument
if [ -n "${1:-}" ]; then
    EXTERNAL_BASE_URL="$1"
fi

# Detect LAN IP if EXTERNAL_BASE_URL not provided
if [ -z "${EXTERNAL_BASE_URL:-}" ]; then
    # Try common methods to detect LAN IP
    if command -v ip &>/dev/null; then
        LAN_IP=$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if ($i=="src") print $(i+1)}' | head -1)
    elif command -v ifconfig &>/dev/null; then
        LAN_IP=$(ifconfig | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | head -1)
    elif command -v hostname &>/dev/null; then
        LAN_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
    fi

    if [ -z "${LAN_IP:-}" ]; then
        echo "Could not auto-detect LAN IP. Set EXTERNAL_BASE_URL manually:"
        echo "  EXTERNAL_BASE_URL=http://<your-ip>:8080 ./start.sh"
        exit 1
    fi

    EXTERNAL_BASE_URL="http://${LAN_IP}:8080"
    echo "Auto-detected LAN IP: ${LAN_IP}"
fi

echo "EXTERNAL_BASE_URL=${EXTERNAL_BASE_URL}"

# Write .env for docker-compose
cat > .env <<EOF
EXTERNAL_BASE_URL=${EXTERNAL_BASE_URL}
EOF

# Template VCT type metadata with resolved external URL
# (Production uses the blob store; the demo serves these as static files via Caddy)
#
# Two safeguards guard against stale `vct/resolved/*.json` files silently surviving a
# failed substitution run (we had one in the wild — see PID.json incident):
#   1. Wipe `vct/resolved/` before regenerating so partial output never leaks across runs.
#   2. Fail-fast if any produced file's top-level `vct` field isn't the expected HTTPS URL.
mkdir -p vct/resolved
rm -f vct/resolved/*.json
for f in vct/*.json; do
    [ -f "$f" ] || continue
    name=$(basename "$f" .json)
    sed -e "s|\"vct\": \"${name}\"|\"vct\": \"${EXTERNAL_BASE_URL}/oid4vci/vct/${name}\"|" \
        -e "s|EXTERNAL_BASE_URL|${EXTERNAL_BASE_URL}|g" \
        "$f" > "vct/resolved/$(basename "$f")"
done

# Verify every resolved file has vct set to the matching HTTPS URL. Wallets that fetch
# the vct URL per SD-JWT VC §6.3 require the returned body's `vct` to equal the URL
# itself — if the sed substitution missed, the wallet silently drops type metadata.
vct_errors=0
for f in vct/resolved/*.json; do
    [ -f "$f" ] || continue
    name=$(basename "$f" .json)
    expected="${EXTERNAL_BASE_URL}/oid4vci/vct/${name}"
    # First line with a top-level `"vct": "..."` field.
    actual=$(grep -m1 -E '^[[:space:]]*"vct":' "$f" | sed -E 's/.*"vct"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/')
    if [ "$actual" != "$expected" ]; then
        echo "ERROR: vct/resolved/${name}.json has vct='${actual}', expected '${expected}'" >&2
        vct_errors=$((vct_errors + 1))
    fi
done
if [ "$vct_errors" -gt 0 ]; then
    echo "Aborting: ${vct_errors} VCT file(s) did not template correctly." >&2
    exit 1
fi

# Build fat JARs (outside Docker for speed and reliability)
IDK_ROOT="$(cd ../../.. && pwd)"
echo "Building fat JARs from ${IDK_ROOT}..."
(cd "${IDK_ROOT}" && ./gradlew \
    :services-oauth2-as-rest:buildFatJar \
    :services-oid4vci-issuer-rest:buildFatJar \
    :services-oid4vp-verifier-rest:buildFatJar \
    :examples-oid4vc-webapp-server:buildFatJar \
    --no-daemon --parallel \
    -Dkotlin.mpp.enabledTargets=jvm \
    -Dkotlin.native.ignoreDisabledTargets=true)

# Copy fat JARs to jars/
mkdir -p jars
cp "${IDK_ROOT}/services/oauth2-as/rest/build/libs/"*-all.jar jars/oauth2-as.jar
cp "${IDK_ROOT}/services/oid4vci-issuer/rest/build/libs/"*-all.jar jars/oid4vci-issuer.jar
cp "${IDK_ROOT}/services/oid4vp-verifier/rest/build/libs/"*-all.jar jars/oid4vp-verifier.jar
cp "${IDK_ROOT}/examples/oid4vc/webapp/server/build/libs/"*-all.jar jars/webapp.jar

# Generate PKCS12 keystores with pre-seeded signing keys
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-e2e-keystore-pass}"
mkdir -p keystores

generate_keystore() {
    local name="$1"
    local keystore="keystores/${name}/keystore.p12"
    shift
    mkdir -p "keystores/${name}"
    if [ -f "$keystore" ]; then
        echo "Keystore $keystore already exists, skipping."
        return
    fi
    echo "Generating keystore: $keystore"
    for alias in "$@"; do
        keytool -genkeypair \
            -alias "$alias" \
            -keyalg EC -groupname secp256r1 \
            -sigalg SHA256withECDSA \
            -validity 3650 \
            -keystore "$keystore" \
            -storetype PKCS12 \
            -storepass "$KEYSTORE_PASSWORD" \
            -dname "CN=IDK E2E $alias, O=Sphereon, C=NL" \
            -noprompt 2>/dev/null
        echo "  Key '$alias' generated"
    done
}

generate_keystore "oauth2-as" "oauth2-server-signing"
generate_keystore "oid4vci-issuer" "TestCredential" "PID" "AgeOver18"
generate_keystore "oid4vp-verifier" "oid4vp-verifier-signing"

echo ""
echo "Starting IDK services environment..."
echo "  Issuer identifier: ${EXTERNAL_BASE_URL}/oid4vci"
echo "  Issuer metadata:   ${EXTERNAL_BASE_URL}/.well-known/openid-credential-issuer/oid4vci"
echo "  AS issuer:          ${EXTERNAL_BASE_URL}/auth"
echo "  AS discovery:       ${EXTERNAL_BASE_URL}/.well-known/oauth-authorization-server/auth"
echo "  Login form:         ${EXTERNAL_BASE_URL}/auth/login"
echo ""

docker compose up -d --build

echo ""
echo "Services starting. Check health:"
echo "  curl ${EXTERNAL_BASE_URL}/auth/health"
