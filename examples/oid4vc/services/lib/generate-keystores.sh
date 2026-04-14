#!/usr/bin/env bash
# Generate PKCS12 keystores with pre-seeded signing keys.
#
# Usage:
#   KEYSTORE_PASSWORD=... KEYSTORES_DIR=keystores
#   generate_keystore <name> <alias>...
#   generate_all_keystores  (creates the three expected keystores)

generate_keystore() {
    local name="$1"
    local keystores_dir="${KEYSTORES_DIR:-keystores}"
    local password="${KEYSTORE_PASSWORD:-e2e-keystore-pass}"
    local keystore="${keystores_dir}/${name}/keystore.p12"
    shift
    mkdir -p "${keystores_dir}/${name}"
    if [ -f "$keystore" ]; then
        echo "Keystore $keystore already exists, skipping."
        return
    fi
    echo "Generating keystore: $keystore"
    local alias
    for alias in "$@"; do
        keytool -genkeypair \
            -alias "$alias" \
            -keyalg EC -groupname secp256r1 \
            -sigalg SHA256withECDSA \
            -validity 3650 \
            -keystore "$keystore" \
            -storetype PKCS12 \
            -storepass "$password" \
            -dname "CN=IDK E2E $alias, O=Sphereon, C=NL" \
            -noprompt 2>/dev/null
        echo "  Key '$alias' generated"
    done
}

generate_all_keystores() {
    generate_keystore "oauth2-as" "oauth2-server-signing"
    generate_keystore "oid4vci-issuer" "TestCredential" "PID" "AgeOver18"
    generate_keystore "oid4vp-verifier" "oid4vp-verifier-signing"
}
