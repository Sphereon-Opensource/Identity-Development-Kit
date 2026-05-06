# Bat-callable thin wrapper around lib/resolve-profile.ps1 that emits two lines
# to stdout:
#   <profile name>
#   <space-separated --env-file <path> pairs (or empty)>
#
# The .bat scripts capture both via `for /f` so they can pass the flags through
# to docker compose verbatim.
#
# Usage from start.bat / start-dev.bat:
#   for /f "usebackq tokens=1*" %%a in (`powershell -NoProfile -ExecutionPolicy Bypass `
#       -File "%~dp0lib\resolve-profile-args.ps1" -RawProfile "%~2"`) do (
#       set "PROFILE_NAME=%%a"
#       set "COMPOSE_PROFILE_ARGS=%%b"
#   )

param(
    [string]$RawProfile = ""
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot/resolve-profile.ps1"

try {
    $resolved = Resolve-Profile $RawProfile
} catch {
    exit 2
}

# Line 1: canonical profile name.
Write-Output $resolved.Name

# Line 2: space-separated compose args (may be empty).
if ($resolved.ComposeArgs.Count -eq 0) {
    Write-Output ""
} else {
    Write-Output ($resolved.ComposeArgs -join " ")
}
