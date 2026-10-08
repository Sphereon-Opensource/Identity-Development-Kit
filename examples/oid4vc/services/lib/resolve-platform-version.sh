#!/usr/bin/env bash
# Resolve Sphereon product versions from platform-version.properties (preferred)
# or gradle.properties version=/gbsVersion= (legacy fallback).
#
# Usage (from bash):
#   # shellcheck source=lib/resolve-platform-version.sh
#   source "${SCRIPT_DIR}/lib/resolve-platform-version.sh"
#   resolve_platform_version "../../../.."   # IDK root relative to caller
#   # sets PLATFORM_VERSION and GBS_VERSION
resolve_platform_version() {
  local root="${1:?IDK root required}"
  local platform_file="${root}/platform-version.properties"
  local gradle_file="${root}/gradle.properties"

  if [ -f "$platform_file" ]; then
    PLATFORM_VERSION="$(grep -E '^platformVersion=' "$platform_file" | cut -d= -f2- | tr -d '\r')"
    GBS_VERSION="$(grep -E '^gbsVersion=' "$platform_file" | cut -d= -f2- | tr -d '\r')"
    echo "PLATFORM_VERSION=${PLATFORM_VERSION} GBS_VERSION=${GBS_VERSION} (from ${platform_file})"
    return 0
  fi

  if [ -f "$gradle_file" ]; then
    PLATFORM_VERSION="$(grep -E '^version=' "$gradle_file" | cut -d= -f2- | tr -d '\r')"
    GBS_VERSION="$(grep -E '^gbsVersion=' "$gradle_file" | cut -d= -f2- | tr -d '\r')"
    echo "PLATFORM_VERSION=${PLATFORM_VERSION} GBS_VERSION=${GBS_VERSION} (from ${gradle_file})"
    return 0
  fi

  return 1
}
