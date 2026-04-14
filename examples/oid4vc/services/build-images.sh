#!/usr/bin/env bash
#
# Build fat JARs and Docker images for the IDK oid4vc example services.
# Does NOT start anything, does NOT push. Useful for CI release jobs and
# as the first half of publish-images.sh.
#
# Tags produced per service:
#   sphereon/idk-<svc>:<IDK_VERSION>
#   sphereon/idk-<svc>:latest
#   sphereon/idk-<svc>:<git-short-sha>
#
# Registry override: REGISTRY=ghcr.io/sphereon ./build-images.sh
# Default registry prefix: sphereon
#
set -euo pipefail
cd "$(dirname "$0")"

IDK_ROOT="$(cd ../../.. && pwd)"
IDK_VERSION="$(grep '^version=' "${IDK_ROOT}/gradle.properties" | cut -d= -f2)"
GIT_SHA="$(git -C "${IDK_ROOT}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
REGISTRY="${REGISTRY:-sphereon}"

echo "IDK_VERSION=${IDK_VERSION}"
echo "GIT_SHA=${GIT_SHA}"
echo "REGISTRY=${REGISTRY}"

# Build fat JARs
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

# svc-name:jar-name mapping (oid4vc-webapp uses jars/webapp.jar, others match directly)
declare -A JARS=(
    [oauth2-as]=jars/oauth2-as.jar
    [oid4vci-issuer]=jars/oid4vci-issuer.jar
    [oid4vp-verifier]=jars/oid4vp-verifier.jar
    [oid4vc-webapp]=jars/webapp.jar
)

for svc in "${!JARS[@]}"; do
    jar="${JARS[$svc]}"
    echo "Building ${REGISTRY}/idk-${svc} from ${jar}..."
    docker build \
        -f Dockerfile.service \
        --build-arg JAR_FILE="${jar}" \
        -t "${REGISTRY}/idk-${svc}:${IDK_VERSION}" \
        -t "${REGISTRY}/idk-${svc}:latest" \
        -t "${REGISTRY}/idk-${svc}:${GIT_SHA}" \
        .
done

echo ""
echo "Built images:"
for svc in "${!JARS[@]}"; do
    echo "  ${REGISTRY}/idk-${svc}:${IDK_VERSION}"
    echo "  ${REGISTRY}/idk-${svc}:latest"
    echo "  ${REGISTRY}/idk-${svc}:${GIT_SHA}"
done
