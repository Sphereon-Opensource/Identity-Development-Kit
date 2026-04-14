# Generate PKCS12 keystores with pre-seeded signing keys.
# Usage: generate-keystores.ps1 [-KeystoresDir <dir>] [-Password <pw>]
param(
    [string]$KeystoresDir = "keystores",
    [string]$Password = $(if ($env:KEYSTORE_PASSWORD) { $env:KEYSTORE_PASSWORD } else { "e2e-keystore-pass" })
)

$ErrorActionPreference = "Stop"

function New-Keystore {
    param([string]$Name, [string[]]$Aliases)
    $dir = Join-Path $KeystoresDir $Name
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
    $keystore = Join-Path $dir "keystore.p12"
    if (Test-Path $keystore) {
        Write-Host "Keystore $keystore already exists, skipping."
        return
    }
    Write-Host "Generating keystore: $keystore"
    foreach ($alias in $Aliases) {
        & keytool -genkeypair `
            -alias $alias `
            -keyalg EC -groupname secp256r1 `
            -sigalg SHA256withECDSA `
            -validity 3650 `
            -keystore $keystore `
            -storetype PKCS12 `
            -storepass $Password `
            -dname "CN=IDK E2E $alias, O=Sphereon, C=NL" `
            -noprompt 2>$null
        if ($LASTEXITCODE -ne 0) { throw "keytool failed for alias $alias" }
        Write-Host "  Key '$alias' generated"
    }
}

New-Keystore -Name "oauth2-as" -Aliases @("oauth2-server-signing")
New-Keystore -Name "oid4vci-issuer" -Aliases @("TestCredential", "PID", "AgeOver18")
New-Keystore -Name "oid4vp-verifier" -Aliases @("oid4vp-verifier-signing")
