#!/usr/bin/env bash
# Resolve a conformance profile name into the docker compose `--env-file` flags
# the start scripts pass to `docker compose up`.
#
# Usage from start scripts:
#   source "${SCRIPT_DIR}/lib/resolve-profile.sh"
#   resolve_profile "${2:-}"
#   docker compose "${COMPOSE_PROFILE_ARGS[@]}" up -d
#
# After `resolve_profile`, two variables are set:
#   - PROFILE_NAME             — canonical name ("default", "did-jwk", …, "haip")
#   - COMPOSE_PROFILE_ARGS     — bash array of `--env-file <path>` pairs (may be empty)

resolve_profile() {
    local raw="${1:-}"
    local profile="${raw:-default}"
    PROFILE_NAME="$profile"
    COMPOSE_PROFILE_ARGS=()

    case "$profile" in
        ""|default)
            PROFILE_NAME=default
            ;;
        did-jwk)
            COMPOSE_PROFILE_ARGS+=(--env-file "profiles/conformance-did-jwk.env")
            ;;
        x509-san-dns)
            COMPOSE_PROFILE_ARGS+=(--env-file "profiles/conformance-x509-san-dns.env")
            ;;
        x509-hash)
            COMPOSE_PROFILE_ARGS+=(--env-file "profiles/conformance-x509-hash.env")
            ;;
        haip)
            # HAIP test plan combo: x509_hash verifier prefix + HAIP-shaped AS auth methods.
            # Credential response encryption is SUPPORTED but not required — most conformance
            # plans run in this mode so plain-credential variants pass alongside encrypted ones.
            COMPOSE_PROFILE_ARGS+=(--env-file "profiles/conformance-x509-hash.env")
            COMPOSE_PROFILE_ARGS+=(--env-file "profiles/conformance-haip-as.env")
            ;;
        haip-enc-required)
            # HAIP combo + credential response encryption REQUIRED. Use this for the OIDF
            # tests that specifically exercise `encryption_required: true` policy. Most plans
            # do NOT need this — the regular haip profile handles those because the suite's
            # `vci_credential_encryption=encrypted` variant works fine when encryption is
            # advertised as supported. `…haip-enc-required.env` overlays the required flag on
            # top of the haip stack.
            COMPOSE_PROFILE_ARGS+=(--env-file "profiles/conformance-x509-hash.env")
            COMPOSE_PROFILE_ARGS+=(--env-file "profiles/conformance-haip-as.env")
            COMPOSE_PROFILE_ARGS+=(--env-file "profiles/conformance-haip-enc-required.env")
            ;;
        haip-key-attest)
            # HAIP combo + OID4VCI 1.0 §7.2 key attestations REQUIRED on EuPid + Mdl.
            # Orthogonal to encryption — leaves the encryption-required flags at false so the
            # wallet sim can still pick the plain-credential variant. Combine with
            # `haip-enc-required` only when you specifically want both required at once.
            COMPOSE_PROFILE_ARGS+=(--env-file "profiles/conformance-x509-hash.env")
            COMPOSE_PROFILE_ARGS+=(--env-file "profiles/conformance-haip-as.env")
            COMPOSE_PROFILE_ARGS+=(--env-file "profiles/conformance-haip-key-attest.env")
            ;;
        oidc-basic)
            # OIDC Core Basic Profile certification (AS / OP role). Plain OAuth2 / OIDC
            # baseline — DO NOT layer this with haip; the haip profile narrows token-endpoint
            # auth methods to `attest_jwt_client_auth` and hides the basic confidential
            # clients. PKCE is loosened from REQUIRED to SUPPORTED for this profile.
            COMPOSE_PROFILE_ARGS+=(--env-file "profiles/conformance-oidc-basic.env")
            ;;
        *)
            echo "ERROR: unknown profile '$profile'." >&2
            echo "Allowed values:" >&2
            echo "  default            — plain demo (did:jwk verifier, plain AS); same as omitting the arg." >&2
            echo "  did-jwk            — explicit did:jwk verifier prefix, plain AS." >&2
            echo "  x509-san-dns       — x509_san_dns verifier prefix (OID4VP plan), plain AS." >&2
            echo "  x509-hash          — x509_hash verifier prefix (OID4VP plan), plain AS." >&2
            echo "  haip               — HAIP combo (encryption SUPPORTED). Default for most conformance plans." >&2
            echo "  haip-enc-required  — HAIP combo with credential_response_encryption.encryption_required=true." >&2
            echo "  haip-key-attest    — HAIP combo with OID4VCI key attestations required on EuPid + Mdl." >&2
            echo "  oidc-basic         — OIDC Core Basic Profile cert (AS role); PKCE loosened to SUPPORTED." >&2
            return 2
            ;;
    esac

    # Docker Compose only auto-loads `.env` when NO `--env-file` is passed.
    # As soon as a profile contributes one, we lose EXTERNAL_BASE_URL / IDK_VERSION
    # auto-load — re-add `.env` first so it's still layered, then the profile files.
    if [ ${#COMPOSE_PROFILE_ARGS[@]} -gt 0 ]; then
        COMPOSE_PROFILE_ARGS=(--env-file ".env" "${COMPOSE_PROFILE_ARGS[@]}")
    fi
}
