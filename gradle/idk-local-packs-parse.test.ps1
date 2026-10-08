$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not (Test-Path "$PSScriptRoot/idk-local-packs.gradle.kts")) {
  throw "missing idk-local-packs.gradle.kts"
}
# Inline expected regex behavior matching the helper (keep in sync):
function Extract-PackModules([string]$settingsPath) {
  Get-Content $settingsPath | Where-Object {
    $t = $_.Trim()
    -not ($t.StartsWith("//") -or $t.StartsWith("/*") -or $t.StartsWith("*"))
  } | ForEach-Object {
    if ($_ -match 'include(?:Local|Mapped)\s*\(\s*"([^"]+)"\s*,') { $Matches[1] }
  }
}
$core = Extract-PackModules "$PSScriptRoot/../core/settings.gradle.kts"
if ($core -notcontains "lib-core-api-public") { throw "expected lib-core-api-public in core extract" }
if ($core -contains "lib-openid-oid4vp-holder-public") { throw "core must not list protocols modules" }
Write-Output "PASS extract core count=$($core.Count)"
