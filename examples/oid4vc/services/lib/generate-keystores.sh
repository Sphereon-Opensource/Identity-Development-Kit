#!/usr/bin/env bash
# Generate PKCS12 keystores with CA-signed leaf signing certs.
#
# MSYS / Git Bash on Windows auto-translates arguments starting with `/`
# (e.g. `-subj "/CN=..."`) into Windows paths. Suppress that for the whole
# script so openssl receives the literal X.500 DN strings the spec expects.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'


#
# A single demo CA (`keystores/ca/ca.{key,crt}`) is created on first run and
# reused for subsequent runs. Each signing alias gets a leaf certificate
# signed by that CA. PKCS12 entries carry ONLY the leaf cert — per HAIP
# 1.0 §5 the trust-anchor (CA) certificate MUST NOT appear in the JOSE
# `x5c` header of the signed Request Object, so we don't bundle it into
# the keystore's per-alias chain. The CA cert is published separately at
# `keystores/ca/ca.crt` for operators to import into the wallet's trust
# store (e.g. the OIDF conformance suite).
#
# Usage:
#   KEYSTORE_PASSWORD=... KEYSTORES_DIR=keystores \
#     generate_keystore <name> [san-dns] -- <alias>...
#   generate_all_keystores  (creates the three demo keystores)

# Generate the demo CA if missing. Returns paths via the global vars
# CA_KEY_PATH and CA_CRT_PATH so callers can reference them.
generate_ca_if_missing() {
    local keystores_dir="${KEYSTORES_DIR:-keystores}"
    local ca_dir="${keystores_dir}/ca"
    CA_KEY_PATH="${ca_dir}/ca.key"
    CA_CRT_PATH="${ca_dir}/ca.crt"
    mkdir -p "$ca_dir"
    if [ -f "$CA_KEY_PATH" ] && [ -f "$CA_CRT_PATH" ]; then
        return
    fi
    echo "Generating demo CA at $CA_CRT_PATH"
    openssl ecparam -genkey -name prime256v1 -out "$CA_KEY_PATH" 2>/dev/null
    openssl req -x509 -new -key "$CA_KEY_PATH" \
        -days 3650 \
        -subj "/CN=IDK E2E CA, O=Sphereon, C=NL" \
        -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
        -addext "keyUsage=critical,keyCertSign,cRLSign" \
        -out "$CA_CRT_PATH" 2>/dev/null
}

# Issue a CA-signed leaf certificate for one alias and append its key+leaf
# (no CA in the chain) to the target PKCS12 keystore.
issue_leaf_into_keystore() {
    local keystore="$1"
    local password="$2"
    local alias="$3"
    local san_dns="$4"
    local tmp_dir="$5"
    # Optional key-usage override (RFC 5280 §4.2.1.3). Defaults to `digitalSignature` because
    # most aliases this script issues are JWS signing keys; ECDH-ES decryption keys should pass
    # `keyAgreement` so the cert's keyUsage matches the JWE-key role. The JWK extracted from
    # the cert doesn't carry X.509 keyUsage on the wire, but downstream validators that DO
    # consult the cert (HAIP §5 trust-anchor checks, mDoc IACA) reject a `digitalSignature`-only
    # cert when the operation is key agreement.
    local key_usage="${6:-digitalSignature}"

    local leaf_key="${tmp_dir}/${alias}.key"
    local leaf_csr="${tmp_dir}/${alias}.csr"
    local leaf_crt="${tmp_dir}/${alias}.crt"
    local leaf_p12="${tmp_dir}/${alias}.p12"
    local extconf="${tmp_dir}/${alias}.ext"

    openssl ecparam -genkey -name prime256v1 -out "$leaf_key" 2>/dev/null

    openssl req -new -key "$leaf_key" \
        -subj "/CN=IDK E2E $alias, O=Sphereon, C=NL" \
        -out "$leaf_csr" 2>/dev/null

    {
        echo "basicConstraints=critical,CA:FALSE"
        echo "keyUsage=critical,${key_usage}"
        echo "extendedKeyUsage=clientAuth"
        if [ -n "$san_dns" ]; then
            echo "subjectAltName=DNS:${san_dns}"
        fi
    } > "$extconf"

    openssl x509 -req -in "$leaf_csr" \
        -CA "$CA_CRT_PATH" -CAkey "$CA_KEY_PATH" -CAcreateserial \
        -days 3650 -sha256 \
        -extfile "$extconf" \
        -out "$leaf_crt" 2>/dev/null

    # Bundle leaf only — no `-certfile $CA_CRT_PATH` so the CA does not end up
    # in the per-alias chain (HAIP §5: trust anchor MUST NOT be in x5c).
    openssl pkcs12 -export \
        -inkey "$leaf_key" -in "$leaf_crt" \
        -name "$alias" \
        -password "pass:${password}" \
        -out "$leaf_p12" 2>/dev/null

    if [ -f "$keystore" ]; then
        keytool -importkeystore \
            -srckeystore "$leaf_p12" -srcstoretype PKCS12 -srcstorepass "$password" \
            -destkeystore "$keystore" -deststoretype PKCS12 -deststorepass "$password" \
            -alias "$alias" -noprompt 2>/dev/null
    else
        mv "$leaf_p12" "$keystore"
    fi
    echo "  CA-signed leaf '$alias' issued"
}

generate_keystore() {
    local name="$1"
    local san_dns="$2"
    shift 2
    local keystores_dir="${KEYSTORES_DIR:-keystores}"
    local password="${KEYSTORE_PASSWORD:-e2e-keystore-pass}"
    local keystore="${keystores_dir}/${name}/keystore.p12"
    mkdir -p "${keystores_dir}/${name}"
    generate_ca_if_missing
    if [ -f "$keystore" ]; then
        echo "Keystore $keystore exists; adding any missing aliases."
    else
        echo "Generating CA-signed keystore: $keystore"
    fi
    _generate_keystore_aliases "$keystore" "$password" "$san_dns" "digitalSignature" "$@"
}

# Issue an ECDH-ES P-256 alias (keyUsage=keyAgreement) into an existing keystore. Used for
# OID4VCI 1.0 §11.2.4 `credential_request_encryption.jwks` — the issuer's public key the
# wallet encrypts credential requests to. JWE/ECDH-ES is key agreement, not signature, so the
# X.509 keyUsage extension differs from the signing aliases issued by `generate_keystore`.
generate_ecdh_alias() {
    local name="$1"
    local alias="$2"
    local keystores_dir="${KEYSTORES_DIR:-keystores}"
    local password="${KEYSTORE_PASSWORD:-e2e-keystore-pass}"
    local keystore="${keystores_dir}/${name}/keystore.p12"
    mkdir -p "${keystores_dir}/${name}"
    generate_ca_if_missing
    _generate_keystore_aliases "$keystore" "$password" "" "keyAgreement" "$alias"
}

# Internal: idempotently add aliases (with the given key_usage) to a keystore. Reused by both
# `generate_keystore` and `generate_ecdh_alias` so the per-alias skip logic stays in one place.
_generate_keystore_aliases() {
    local keystore="$1"
    local password="$2"
    local san_dns="$3"
    local key_usage="$4"
    shift 4
    # Project-relative tempdir so MSYS (Git Bash) doesn't wrongly translate
    # `/tmp/...` paths between Bash redirects and the native Windows openssl
    # binary — the two end up reading/writing different physical paths,
    # which is why mktemp -d under `/tmp/` fails silently on Windows hosts.
    local keystore_parent
    keystore_parent="$(dirname "$keystore")"
    local tmp_dir="${keystore_parent}/.work-$$"
    mkdir -p "$tmp_dir"
    local alias
    for alias in "$@"; do
        # Idempotent per alias: skip aliases already present so existing keystores
        # gracefully gain new aliases (e.g. when the issuer config grows a new
        # credential) without forcing operators to delete the file by hand.
        if [ -f "$keystore" ] && keytool -list -keystore "$keystore" -storepass "$password" \
                -alias "$alias" -storetype PKCS12 >/dev/null 2>&1; then
            echo "  Alias '$alias' already present, skipping"
            continue
        fi
        issue_leaf_into_keystore "$keystore" "$password" "$alias" "$san_dns" "$tmp_dir" "$key_usage"
    done
    rm -rf "$tmp_dir"
}

generate_all_keystores() {
    local verifier_san_dns="${VERIFIER_SAN_DNS:-verifier.example.com}"
    local keystores_dir="${KEYSTORES_DIR:-keystores}"
    local ca_dir="${keystores_dir}/ca"

    # CA + leaves must stay in lockstep — a leaf signed by a vanished CA can't be
    # validated by any wallet that imported the original CA cert. If the CA is
    # absent (fresh checkout, or operator deleted it to rotate the trust anchor),
    # purge every leaf keystore so the next pass re-issues them under the new CA.
    if [ ! -f "${ca_dir}/ca.crt" ] || [ ! -f "${ca_dir}/ca.key" ]; then
        local name
        for name in oauth2-as oid4vci-issuer oid4vp-verifier; do
            if [ -f "${keystores_dir}/${name}/keystore.p12" ]; then
                echo "CA missing — discarding stale leaf keystore ${keystores_dir}/${name}/keystore.p12"
                rm -f "${keystores_dir}/${name}/keystore.p12"
            fi
        done
        # Also purge the wallet-attester JWKS — it would still verify against the new CA
        # because we re-issue from the same CA below, but a stale JWKS from a previous CA
        # rotation would verify-fail at the AS. Cheap to regenerate.
        rm -f "${keystores_dir}/wallet-attester/wallet-attester.jwks.json"
        # Same reasoning for the key-attester JWKS used by the OID4VCI key-attestation flow.
        rm -f "${keystores_dir}/key-attester/key-attester.jwks.json"
    fi

    generate_keystore "oauth2-as" "" "oauth2-server-signing"
    generate_keystore "oid4vci-issuer" "" "TestCredential" "PID" "AgeOver18" "Mdl"
    # OID4VCI 1.0 §11.2.4 credential_request_encryption: ECDH-ES P-256 keypair the issuer
    # publishes as `credential_request_encryption.jwks` so wallets can encrypt credential
    # requests TO the issuer. Separate alias from the signing keys above because keyUsage
    # differs (keyAgreement vs digitalSignature).
    generate_ecdh_alias "oid4vci-issuer" "oid4vci-request-decryption"
    generate_keystore "oid4vp-verifier" "$verifier_san_dns" "oid4vp-verifier-signing"

    generate_wallet_attester_jwks_if_missing
    generate_key_attester_jwks_if_missing

    # Surface the CA path so operators can find the trust anchor without
    # hunting through the script. Wallets / conformance suites need this cert
    # imported as a trust anchor (HAIP §5: trust anchor is out of band, MUST
    # NOT be in the JOSE x5c header).
    if [ -f "${ca_dir}/ca.crt" ]; then
        echo ""
        echo "Demo trust anchor (import into wallet trust store):"
        echo "  ${ca_dir}/ca.crt"
    fi
}

# Generate the wallet-attester signing key + leaf cert chain (anchored at the demo CA),
# and emit a JWKS document the OIDF conformance plan UI consumes verbatim in the
# `Client Attester Keys JWKS` field. Per HAIP §4.4.1 the wallet attestation MUST carry the
# leaf cert in the JOSE x5c header (and MUST NOT include the trust anchor); the OIDF
# `CreateClientAttestationJwt` conformance step copies x5c straight from the JWK, so the
# JWKS we emit must include the chain.
#
# Side effect: drops the demo CA PEM at ${TRUST_ANCHORS_DIR:-trust-anchors}/wallet-attester-ca.pem
# so the AS — when run under the HAIP profile — picks it up via the docker-compose
# trust-anchors volume mount and validates incoming x5c chains against it.
generate_wallet_attester_jwks_if_missing() {
    local keystores_dir="${KEYSTORES_DIR:-keystores}"
    local out_dir="${keystores_dir}/wallet-attester"
    local jwks_path="${out_dir}/wallet-attester.jwks.json"
    local trust_anchors_dir="${TRUST_ANCHORS_DIR:-trust-anchors}"
    local ca_target="${trust_anchors_dir}/wallet-attester-ca.pem"

    mkdir -p "$out_dir" "$trust_anchors_dir"

    # Always refresh the CA copy under trust-anchors/ so `start-dev.sh haip` can mount
    # the same anchor the JWKS chains to. Cheap, idempotent.
    if [ -f "$CA_CRT_PATH" ]; then
        cp "$CA_CRT_PATH" "$ca_target"
    fi

    if [ -f "$jwks_path" ]; then
        echo "Wallet-attester JWKS exists; reusing $jwks_path"
        return
    fi

    echo "Generating wallet-attester JWKS for OIDF conformance HAIP plan"
    local tmp_dir="${out_dir}/.work-$$"
    mkdir -p "$tmp_dir"
    local key_pem="${tmp_dir}/wallet-attester.key.pem"
    local csr="${tmp_dir}/wallet-attester.csr"
    local leaf_pem="${tmp_dir}/wallet-attester.crt.pem"
    local extconf="${tmp_dir}/wallet-attester.ext"

    openssl ecparam -genkey -name prime256v1 -out "$key_pem" 2>/dev/null
    openssl req -new -key "$key_pem" \
        -subj "/CN=IDK E2E Wallet Attester, O=Sphereon, C=NL" \
        -out "$csr" 2>/dev/null

    # Wallet-attester leaf cert profile: digitalSignature only, no SAN required (HAIP
    # §4.4.1 doesn't constrain the SAN; the AS validates by chain not by hostname).
    {
        echo "basicConstraints=critical,CA:FALSE"
        echo "keyUsage=critical,digitalSignature"
    } > "$extconf"

    openssl x509 -req -in "$csr" \
        -CA "$CA_CRT_PATH" -CAkey "$CA_KEY_PATH" -CAcreateserial \
        -days 3650 -sha256 \
        -extfile "$extconf" \
        -out "$leaf_pem" 2>/dev/null

    # Convert key + leaf to JWK with x5c (private key has `d`, leaf goes in `x5c`).
    local priv_hex pub_hex x_hex y_hex d_b64u x_b64u y_b64u leaf_b64
    priv_hex="$(openssl ec -in "$key_pem" -text -noout 2>/dev/null \
        | awk '/^priv:/{flag=1;next}/^pub:/{flag=0}flag' \
        | tr -d ' :\n')"
    pub_hex="$(openssl ec -in "$key_pem" -text -noout 2>/dev/null \
        | awk '/^pub:/{flag=1;next}/^ASN1 OID/{flag=0}flag' \
        | tr -d ' :\n')"
    # Strip the leading `04` (uncompressed point indicator) and split into 32-byte X || Y.
    pub_hex="${pub_hex#04}"
    x_hex="${pub_hex:0:64}"
    y_hex="${pub_hex:64:64}"
    # EC P-256 private values are 32 bytes; openssl may prepend a leading 00 when the
    # high bit is set — keep the trailing 64 hex chars to normalize either way.
    priv_hex="${priv_hex: -64}"

    hex_to_base64url() {
        printf '%s' "$1" | xxd -r -p | base64 -w0 | tr '+/' '-_' | tr -d '='
    }
    d_b64u="$(hex_to_base64url "$priv_hex")"
    x_b64u="$(hex_to_base64url "$x_hex")"
    y_b64u="$(hex_to_base64url "$y_hex")"
    leaf_b64="$(openssl x509 -in "$leaf_pem" -outform DER 2>/dev/null | base64 -w0)"

    # JWK with x5c — single-line entries so the conformance UI accepts it as-is. Trust
    # anchor deliberately omitted from x5c per HAIP §4.4.1.
    cat > "$jwks_path" <<EOF
{
  "keys": [
    {
      "kty": "EC",
      "crv": "P-256",
      "use": "sig",
      "alg": "ES256",
      "kid": "wallet-attester",
      "x": "${x_b64u}",
      "y": "${y_b64u}",
      "d": "${d_b64u}",
      "x5c": ["${leaf_b64}"]
    }
  ]
}
EOF

    rm -rf "$tmp_dir"
    echo "  Wrote ${jwks_path}"
    echo "  Wrote ${ca_target}"
}

# Generate the key-attester signing key + leaf cert chain for OID4VCI 1.0 §7.2 key
# attestations. Distinct from the wallet attester (above) — different signer identity,
# separate output JWKS, separate trust-anchor file — but issued from the same demo CA
# so the keystore-bootstrap pipeline stays single-source.
#
# Operator workflow: paste the contents of the produced JWKS file into the OIDF
# conformance plan UI's `vci.key_attestation_jwks` field. The plan's
# `VCIGenerateKeyAttestationIfNecessary` step always emits the leaf cert in the JWS
# `x5c` header (signJWT(..., includeX5c=true, errorIfX5cMissing=true)), and our
# verifier validates the chain against trust-anchors/key-attester-ca.pem via
# lib/trust/x509.
generate_key_attester_jwks_if_missing() {
    local keystores_dir="${KEYSTORES_DIR:-keystores}"
    local out_dir="${keystores_dir}/key-attester"
    local jwks_path="${out_dir}/key-attester.jwks.json"
    local trust_anchors_dir="${TRUST_ANCHORS_DIR:-trust-anchors}"
    local ca_target="${trust_anchors_dir}/key-attester-ca.pem"

    mkdir -p "$out_dir" "$trust_anchors_dir"

    if [ -f "$CA_CRT_PATH" ]; then
        cp "$CA_CRT_PATH" "$ca_target"
    fi

    if [ -f "$jwks_path" ]; then
        echo "Key-attester JWKS exists; reusing $jwks_path"
        return
    fi

    echo "Generating key-attester JWKS for OID4VCI key-attestation conformance flow"
    local tmp_dir="${out_dir}/.work-$$"
    mkdir -p "$tmp_dir"
    local key_pem="${tmp_dir}/key-attester.key.pem"
    local csr="${tmp_dir}/key-attester.csr"
    local leaf_pem="${tmp_dir}/key-attester.crt.pem"
    local extconf="${tmp_dir}/key-attester.ext"

    openssl ecparam -genkey -name prime256v1 -out "$key_pem" 2>/dev/null
    openssl req -new -key "$key_pem" \
        -subj "/CN=IDK E2E Key Attester, O=Sphereon, C=NL" \
        -out "$csr" 2>/dev/null

    {
        echo "basicConstraints=critical,CA:FALSE"
        echo "keyUsage=critical,digitalSignature"
    } > "$extconf"

    openssl x509 -req -in "$csr" \
        -CA "$CA_CRT_PATH" -CAkey "$CA_KEY_PATH" -CAcreateserial \
        -days 3650 -sha256 \
        -extfile "$extconf" \
        -out "$leaf_pem" 2>/dev/null

    local priv_hex pub_hex x_hex y_hex d_b64u x_b64u y_b64u leaf_b64
    priv_hex="$(openssl ec -in "$key_pem" -text -noout 2>/dev/null \
        | awk '/^priv:/{flag=1;next}/^pub:/{flag=0}flag' \
        | tr -d ' :\n')"
    pub_hex="$(openssl ec -in "$key_pem" -text -noout 2>/dev/null \
        | awk '/^pub:/{flag=1;next}/^ASN1 OID/{flag=0}flag' \
        | tr -d ' :\n')"
    pub_hex="${pub_hex#04}"
    x_hex="${pub_hex:0:64}"
    y_hex="${pub_hex:64:64}"
    priv_hex="${priv_hex: -64}"

    hex_to_base64url() {
        printf '%s' "$1" | xxd -r -p | base64 -w0 | tr '+/' '-_' | tr -d '='
    }
    d_b64u="$(hex_to_base64url "$priv_hex")"
    x_b64u="$(hex_to_base64url "$x_hex")"
    y_b64u="$(hex_to_base64url "$y_hex")"
    leaf_b64="$(openssl x509 -in "$leaf_pem" -outform DER 2>/dev/null | base64 -w0)"

    cat > "$jwks_path" <<EOF
{
  "keys": [
    {
      "kty": "EC",
      "crv": "P-256",
      "use": "sig",
      "alg": "ES256",
      "kid": "key-attester",
      "x": "${x_b64u}",
      "y": "${y_b64u}",
      "d": "${d_b64u}",
      "x5c": ["${leaf_b64}"]
    }
  ]
}
EOF

    rm -rf "$tmp_dir"
    echo "  Wrote ${jwks_path}"
    echo "  Wrote ${ca_target}"
}
