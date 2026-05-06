# Resolve a conformance profile name into the docker compose `--env-file` flags
# the start scripts pass to `docker compose up`.
#
# Usage:
#   . "$SCRIPT_DIR\lib\resolve-profile.ps1"
#   $resolved = Resolve-Profile $RawProfile
#   docker compose @($resolved.ComposeArgs) up -d
#
# Returns a PSCustomObject with:
#   - Name        canonical name ("default", "did-jwk", …, "haip")
#   - ComposeArgs string[] of `--env-file <path>` pairs (may be empty)

function Resolve-Profile {
    param(
        [string]$RawProfile = ""
    )

    $profile = if ([string]::IsNullOrWhiteSpace($RawProfile)) { "default" } else { $RawProfile }
    $args = @()

    # Docker Compose only auto-loads `.env` when NO `--env-file` is passed.
    # As soon as a profile contributes one, we lose EXTERNAL_BASE_URL / IDK_VERSION
    # auto-load — re-add `.env` first so it's still layered, then the profile files.
    $profileArgs = @()

    switch ($profile) {
        "" {
            $profile = "default"
        }
        "default" {
            # No extra env files; preserves today's behaviour exactly (auto-loads `.env`).
        }
        "did-jwk" {
            $profileArgs = @("--env-file", "profiles/conformance-did-jwk.env")
        }
        "x509-san-dns" {
            $profileArgs = @("--env-file", "profiles/conformance-x509-san-dns.env")
        }
        "x509-hash" {
            $profileArgs = @("--env-file", "profiles/conformance-x509-hash.env")
        }
        "haip" {
            # HAIP test plan combo: x509_hash verifier prefix + HAIP-shaped AS auth methods.
            # Credential response encryption is SUPPORTED but not required (default for most plans).
            $profileArgs = @(
                "--env-file", "profiles/conformance-x509-hash.env",
                "--env-file", "profiles/conformance-haip-as.env"
            )
        }
        "haip-enc-required" {
            # HAIP combo + credential response encryption REQUIRED. Use for OIDF plans that
            # specifically exercise `encryption_required: true` policy.
            $profileArgs = @(
                "--env-file", "profiles/conformance-x509-hash.env",
                "--env-file", "profiles/conformance-haip-as.env",
                "--env-file", "profiles/conformance-haip-enc-required.env"
            )
        }
        default {
            Write-Host "ERROR: unknown profile '$profile'." -ForegroundColor Red
            Write-Host "Allowed values:"
            Write-Host "  default            - plain demo (did:jwk verifier, plain AS); same as omitting the arg."
            Write-Host "  did-jwk            - explicit did:jwk verifier prefix, plain AS."
            Write-Host "  x509-san-dns       - x509_san_dns verifier prefix (OID4VP plan), plain AS."
            Write-Host "  x509-hash          - x509_hash verifier prefix (OID4VP plan), plain AS."
            Write-Host "  haip               - HAIP combo (encryption SUPPORTED). Default for most conformance plans."
            Write-Host "  haip-enc-required  - HAIP combo with credential_response_encryption.encryption_required=true."
            throw "Unknown profile: $profile"
        }
    }

    if ($profileArgs.Count -gt 0) {
        $args = @("--env-file", ".env") + $profileArgs
    }

    return [pscustomobject]@{
        Name        = $profile
        ComposeArgs = $args
    }
}
