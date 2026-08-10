# Generate PKCS12 keystores with CA-signed leaf signing certs.
#
# A single demo CA (`keystores\ca\ca.{key,crt}`) is created on first run and
# reused for subsequent runs. Each signing alias gets a leaf certificate
# signed by that CA. PKCS12 entries carry ONLY the leaf cert — per HAIP
# 1.0 §5 the trust-anchor (CA) certificate MUST NOT appear in the JOSE
# `x5c` header of the signed Request Object. The CA cert is published at
# `keystores\ca\ca.crt` for operators to import into the wallet's trust
# store (e.g. the OIDF conformance suite).
#
# Usage: generate-keystores.ps1 [-KeystoresDir <dir>] [-Password <pw>] [-VerifierSanDns <dns>]
param(
    [string]$KeystoresDir = "keystores",
    [string]$Password = $(if ($env:KEYSTORE_PASSWORD) { $env:KEYSTORE_PASSWORD } else { "e2e-keystore-pass" }),
    [string]$VerifierSanDns = "verifier.example.com"
)

$ErrorActionPreference = "Stop"

# Native commands that write to stderr (openssl, keytool) trigger
# NativeCommandError under EAP=Stop in WinPS 5.1. Drop EAP locally for
# the duration of the call, redirect stderr, and gate on $LASTEXITCODE.
function Invoke-Native {
    param([scriptblock]$Block, [string]$ErrorMessage)
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Block 2>&1 | Out-Null
    } finally {
        $ErrorActionPreference = $prevEAP
    }
    if ($LASTEXITCODE -ne 0) { throw $ErrorMessage }
}

$script:CaKeyPath = $null
$script:CaCrtPath = $null

function Initialize-DemoCA {
    $caDir = Join-Path $KeystoresDir "ca"
    if (-not (Test-Path $caDir)) { New-Item -ItemType Directory -Path $caDir | Out-Null }
    $script:CaKeyPath = Join-Path $caDir "ca.key"
    $script:CaCrtPath = Join-Path $caDir "ca.crt"
    if ((Test-Path $script:CaKeyPath) -and (Test-Path $script:CaCrtPath)) { return }
    Write-Host "Generating demo CA at $script:CaCrtPath"
    Invoke-Native { openssl ecparam -genkey -name prime256v1 -out $script:CaKeyPath } "openssl ecparam (CA) failed"
    Invoke-Native {
        openssl req -x509 -new -key $script:CaKeyPath `
            -days 3650 `
            -subj "/CN=IDK E2E CA, O=Sphereon, C=NL" `
            -addext "basicConstraints=critical,CA:TRUE,pathlen:0" `
            -addext "keyUsage=critical,keyCertSign,cRLSign" `
            -out $script:CaCrtPath
    } "openssl req (CA) failed"
}

function Add-LeafToKeystore {
    param(
        [string]$Keystore,
        [string]$Alias,
        [string]$SanDns,
        [string]$TmpDir,
        # Optional X.509 keyUsage (RFC 5280 §4.2.1.3). Defaults to `digitalSignature` because
        # most aliases are JWS signing keys; ECDH-ES decryption keys pass `keyAgreement`.
        [string]$KeyUsage = "digitalSignature"
    )

    $leafKey = Join-Path $TmpDir "$Alias.key"
    $leafCsr = Join-Path $TmpDir "$Alias.csr"
    $leafCrt = Join-Path $TmpDir "$Alias.crt"
    $leafP12 = Join-Path $TmpDir "$Alias.p12"
    $extConf = Join-Path $TmpDir "$Alias.ext"

    Invoke-Native { openssl ecparam -genkey -name prime256v1 -out $leafKey } "openssl ecparam (leaf $Alias) failed"

    Invoke-Native {
        openssl req -new -key $leafKey `
            -subj "/CN=IDK E2E $Alias, O=Sphereon, C=NL" `
            -out $leafCsr
    } "openssl req (leaf $Alias) failed"

    $extLines = @(
        "basicConstraints=critical,CA:FALSE",
        "keyUsage=critical,$KeyUsage",
        "extendedKeyUsage=clientAuth"
    )
    if ($SanDns) { $extLines += "subjectAltName=DNS:$SanDns" }
    Set-Content -Path $extConf -Value $extLines

    Invoke-Native {
        openssl x509 -req -in $leafCsr `
            -CA $script:CaCrtPath -CAkey $script:CaKeyPath -CAcreateserial `
            -days 3650 -sha256 `
            -extfile $extConf `
            -out $leafCrt
    } "openssl x509 (leaf $Alias) failed"

    # Bundle leaf only — no `-certfile $script:CaCrtPath` so the CA does not end up
    # in the per-alias chain (HAIP §5: trust anchor MUST NOT be in x5c).
    Invoke-Native {
        openssl pkcs12 -export `
            -inkey $leafKey -in $leafCrt `
            -name $Alias `
            -password "pass:$Password" `
            -out $leafP12
    } "openssl pkcs12 (leaf $Alias) failed"

    if (Test-Path $Keystore) {
        Invoke-Native {
            keytool -importkeystore `
                -srckeystore $leafP12 -srcstoretype PKCS12 -srcstorepass $Password `
                -destkeystore $Keystore -deststoretype PKCS12 -deststorepass $Password `
                -alias $Alias -noprompt
        } "keytool -importkeystore failed for alias $Alias"
    } else {
        Move-Item -Path $leafP12 -Destination $Keystore
    }
    Write-Host "  CA-signed leaf '$Alias' issued"
}

function Test-AliasPresent {
    param([string]$Keystore, [string]$Alias)
    if (-not (Test-Path $Keystore)) { return $false }
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & keytool -list -keystore $Keystore -storepass $Password -alias $Alias -storetype PKCS12 2>&1 | Out-Null
    } finally {
        $ErrorActionPreference = $prevEAP
    }
    return ($LASTEXITCODE -eq 0)
}

function New-Keystore {
    param(
        [string]$Name,
        [string[]]$Aliases,
        [string]$SanDns,
        # Defaults to `digitalSignature` for the JWS signing aliases; pass `keyAgreement`
        # via `Add-EcdhAliasToKeystore` for ECDH-ES decryption keys.
        [string]$KeyUsage = "digitalSignature"
    )
    # Software KMS resolves every file-backed keystore below a tenant directory.
    # These example services use FixedTenantResolver("default"), so seed the exact
    # file the runtime opens from its configured <service>/keystore.p12 base path.
    $dir = Join-Path (Join-Path $KeystoresDir $Name) "default"
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
    $keystore = Join-Path $dir "keystore.p12"
    Initialize-DemoCA
    if (Test-Path $keystore) {
        Write-Host "Keystore $keystore exists; adding any missing aliases."
    } else {
        Write-Host "Generating CA-signed keystore: $keystore"
    }
    $tmpDir = New-Item -ItemType Directory -Path (Join-Path $env:TEMP ("idk-keystores-" + [Guid]::NewGuid().ToString("N")))
    try {
        foreach ($alias in $Aliases) {
            # Idempotent per alias: skip aliases already present so existing keystores
            # gracefully gain new aliases (e.g. when the issuer config grows a new
            # credential) without forcing operators to delete the file by hand.
            if (Test-AliasPresent -Keystore $keystore -Alias $alias) {
                Write-Host "  Alias '$alias' already present, skipping"
                continue
            }
            Add-LeafToKeystore -Keystore $keystore -Alias $alias -SanDns $SanDns -TmpDir $tmpDir.FullName -KeyUsage $KeyUsage
        }
    } finally {
        Remove-Item -Path $tmpDir.FullName -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# Wrapper that adds an ECDH-ES P-256 alias (keyUsage=keyAgreement) to an existing keystore.
# Used for OID4VCI 1.0 §11.2.4 `credential_request_encryption.jwks` — the issuer's public key
# the wallet encrypts credential requests to.
function Add-EcdhAliasToKeystore {
    param([string]$Name, [string]$Alias)
    New-Keystore -Name $Name -Aliases @($Alias) -SanDns "" -KeyUsage "keyAgreement"
}

$caCrt = Join-Path (Join-Path $KeystoresDir "ca") "ca.crt"
$caKey = Join-Path (Join-Path $KeystoresDir "ca") "ca.key"

# CA + leaves must stay in lockstep — a leaf signed by a vanished CA can't be
# validated by any wallet that imported the original CA cert. If the CA is
# absent (fresh checkout, or operator deleted it to rotate the trust anchor),
# purge every leaf keystore so the next pass re-issues them under the new CA.
if (-not ((Test-Path $caCrt) -and (Test-Path $caKey))) {
    foreach ($name in @("oauth2-as", "oid4vci-issuer", "oid4vp-verifier")) {
        $stale = Join-Path (Join-Path (Join-Path $KeystoresDir $name) "default") "keystore.p12"
        if (Test-Path $stale) {
            Write-Host "CA missing — discarding stale leaf keystore $stale"
            Remove-Item -Path $stale -Force
        }
    }
}

New-Keystore -Name "oauth2-as" -Aliases @("oauth2-server-signing")
New-Keystore -Name "oid4vci-issuer" -Aliases @("TestCredential", "PID", "AgeOver18", "Mdl")
# OID4VCI 1.0 §11.2.4 credential_request_encryption: ECDH-ES P-256 keypair the issuer publishes
# as `credential_request_encryption.jwks`. Separate alias from the signing keys above because
# keyUsage differs (keyAgreement vs digitalSignature).
Add-EcdhAliasToKeystore -Name "oid4vci-issuer" -Alias "oid4vci-request-decryption"
New-Keystore -Name "oid4vp-verifier" -Aliases @("oid4vp-verifier-signing") -SanDns $VerifierSanDns

if (Test-Path $caCrt) {
    Write-Host ""
    Write-Host "Demo trust anchor (import into wallet trust store):"
    Write-Host "  $caCrt"
}
