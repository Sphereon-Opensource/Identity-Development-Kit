# Resolve EXTERNAL_BASE_URL: explicit arg > env var > LAN IP auto-detect.
# Writes the resolved URL to stdout. Exit 1 on failure.
param([string]$BaseUrl = "")

if ($BaseUrl -ne "") {
    Write-Output $BaseUrl
    exit 0
}

if ($env:EXTERNAL_BASE_URL) {
    Write-Output $env:EXTERNAL_BASE_URL
    exit 0
}

try {
    $ip = (Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.InterfaceAlias -notmatch 'Loopback' -and $_.PrefixOrigin -ne 'WellKnown' } |
        Sort-Object -Property InterfaceMetric |
        Select-Object -First 1).IPAddress
} catch { $ip = $null }

if (-not $ip) {
    Write-Error "Could not auto-detect LAN IP. Set EXTERNAL_BASE_URL manually."
    exit 1
}

Write-Output "http://${ip}:8080"
