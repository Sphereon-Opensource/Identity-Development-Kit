# Template VCT type metadata with the resolved external URL.
# Usage: template-vcts.ps1 -BaseUrl <url> [-VctDir <dir>]
param(
    [Parameter(Mandatory=$true)][string]$BaseUrl,
    [string]$VctDir = "vct"
)

$ErrorActionPreference = "Stop"

$resolved = Join-Path $VctDir "resolved"
if (-not (Test-Path $resolved)) { New-Item -ItemType Directory -Path $resolved | Out-Null }
Get-ChildItem -Path $resolved -Filter "*.json" -ErrorAction SilentlyContinue | Remove-Item -Force

Get-ChildItem -Path $VctDir -Filter "*.json" | ForEach-Object {
    $name = $_.BaseName
    $content = Get-Content $_.FullName -Raw
    $content = $content -replace "`"vct`": `"$name`"", "`"vct`": `"$BaseUrl/oid4vci/vct/$name`""
    $content = $content -replace "EXTERNAL_BASE_URL", $BaseUrl
    Set-Content -Path (Join-Path $resolved "$name.json") -Value $content -NoNewline
}

$errors = 0
Get-ChildItem -Path $resolved -Filter "*.json" | ForEach-Object {
    $name = $_.BaseName
    $expected = "$BaseUrl/oid4vci/vct/$name"
    $match = Select-String -Path $_.FullName -Pattern '^\s*"vct"\s*:\s*"([^"]*)"' | Select-Object -First 1
    $actual = if ($match) { $match.Matches[0].Groups[1].Value } else { "" }
    if ($actual -ne $expected) {
        Write-Error "vct/resolved/$name.json has vct='$actual', expected '$expected'"
        $errors++
    }
}
if ($errors -gt 0) { exit 1 }
