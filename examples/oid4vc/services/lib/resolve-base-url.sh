#!/usr/bin/env bash
# Resolve EXTERNAL_BASE_URL: explicit arg > env var > LAN IP auto-detect.
#
# Usage: source this file, then call resolve_external_base_url "$@"
# Exports EXTERNAL_BASE_URL on success; exits non-zero on failure.

resolve_external_base_url() {
    if [ -n "${1:-}" ]; then
        EXTERNAL_BASE_URL="$1"
    fi

    if [ -z "${EXTERNAL_BASE_URL:-}" ]; then
        local LAN_IP=""
        if command -v ip >/dev/null 2>&1; then
            LAN_IP=$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if ($i=="src") print $(i+1)}' | head -1)
        elif command -v ifconfig >/dev/null 2>&1; then
            LAN_IP=$(ifconfig | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | head -1)
        elif command -v hostname >/dev/null 2>&1; then
            LAN_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
        fi

        if [ -z "$LAN_IP" ]; then
            echo "Could not auto-detect LAN IP. Set EXTERNAL_BASE_URL manually:" >&2
            echo "  EXTERNAL_BASE_URL=http://<your-ip>:8080 $0" >&2
            return 1
        fi

        EXTERNAL_BASE_URL="http://${LAN_IP}:8080"
        echo "Auto-detected LAN IP: ${LAN_IP}"
    fi

    export EXTERNAL_BASE_URL
    echo "EXTERNAL_BASE_URL=${EXTERNAL_BASE_URL}"
}
