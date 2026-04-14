#!/usr/bin/env bash
#
# Publish IDK oid4vc example service images to a Docker registry.
#
# Workflow: runs build-images.sh, then docker push for each tag.
# SNAPSHOT guard: refuses to push *-SNAPSHOT versions unless --allow-snapshot.
# :latest is only pushed for non-SNAPSHOT releases (to protect the stable tag).
#
# Usage:
#   docker login docker.io
#   ./publish-images.sh                         # Release build
#   ./publish-images.sh --allow-snapshot        # Push SNAPSHOT internally
#   ./publish-images.sh --dirty                 # Allow uncommitted changes
#   REGISTRY=ghcr.io/sphereon ./publish-images.sh
#
set -euo pipefail
cd "$(dirname "$0")"

ALLOW_SNAPSHOT=0
ALLOW_DIRTY=0
for arg in "$@"; do
    case "$arg" in
        --allow-snapshot) ALLOW_SNAPSHOT=1 ;;
        --dirty) ALLOW_DIRTY=1 ;;
        *) echo "Unknown arg: $arg" >&2; exit 2 ;;
    esac
done

IDK_ROOT="$(cd ../../.. && pwd)"
IDK_VERSION="$(grep '^version=' "${IDK_ROOT}/gradle.properties" | cut -d= -f2)"
GIT_SHA="$(git -C "${IDK_ROOT}" rev-parse --short HEAD)"
REGISTRY="${REGISTRY:-sphereon}"

if [ "$ALLOW_DIRTY" -eq 0 ]; then
    if ! git -C "${IDK_ROOT}" diff-index --quiet HEAD --; then
        echo "ERROR: IDK source tree has uncommitted changes. Pass --dirty to override." >&2
        exit 1
    fi
fi

IS_SNAPSHOT=0
case "$IDK_VERSION" in
    *-SNAPSHOT) IS_SNAPSHOT=1 ;;
esac

if [ "$IS_SNAPSHOT" -eq 1 ] && [ "$ALLOW_SNAPSHOT" -eq 0 ]; then
    echo "ERROR: Refusing to push SNAPSHOT version ${IDK_VERSION}. Pass --allow-snapshot to override." >&2
    exit 1
fi

REGISTRY="${REGISTRY}" ./build-images.sh

SERVICES=(oauth2-as oid4vci-issuer oid4vp-verifier oid4vc-webapp)
for svc in "${SERVICES[@]}"; do
    docker push "${REGISTRY}/idk-${svc}:${IDK_VERSION}"
    docker push "${REGISTRY}/idk-${svc}:${GIT_SHA}"
    if [ "$IS_SNAPSHOT" -eq 0 ]; then
        docker push "${REGISTRY}/idk-${svc}:latest"
    else
        echo "Skipping :latest push for SNAPSHOT ${REGISTRY}/idk-${svc}"
    fi
done

echo ""
echo "Published ${#SERVICES[@]} images at version ${IDK_VERSION} to ${REGISTRY}."
