$ErrorActionPreference = 'Stop'

$moduleRoot = $PSScriptRoot
$buildFile = Join-Path $moduleRoot 'build.gradle.kts'
$commonMain = Join-Path $moduleRoot 'src/commonMain'

$forbiddenDeps = @(
    'libWalletInteractionImpl'
    'libDataStoreKvImpl'
    'libWalletInteractionProtocolOid4vci'
    'libWalletInteractionProtocolOid4vp'
    'libWalletInteractionProtocolIso18013'
    'libOpenidOid4vpVerifierImpl'
    'libJsonLdLoaderImpl'
    'projects.libWalletInteractionImpl'
    'projects.libDataStoreKvImpl'
)

$forbiddenSourceTokens = @(
    'DefaultWalletInteractionEngine'
    'LocalWalletInteractionClient'
    'StoreBackedWalletInteractionSensitiveInputAuthority'
    'SessionWalletAttendedAuthorizationRegistry'
    'DefaultWalletCounterpartyEncounterRegistryModule'
    'DefaultWalletHolderOptionalSeamsModule'
    'DefaultOid4vciIssuanceOptionsProvider'
    'DefaultOid4vpWalletConfigProvider'
)

if (-not (Test-Path -LiteralPath $buildFile)) {
    throw "holder-wiring boundary guard missing build file: $buildFile"
}
if (-not (Test-Path -LiteralPath $commonMain)) {
    throw "holder-wiring boundary guard missing commonMain: $commonMain"
}

$buildText = Get-Content -Raw -LiteralPath $buildFile
# Limit dependency checks to the commonMain dependencies block.
$commonMainDeps = [regex]::Match(
    $buildText,
    '(?s)val commonMain by getting \{.*?dependencies \{(.*?)\}'
).Groups[1].Value
if ([string]::IsNullOrWhiteSpace($commonMainDeps)) {
    throw 'holder-wiring boundary guard could not locate commonMain dependencies block'
}

$depHits = 0
foreach ($dep in $forbiddenDeps) {
    $depHits += ([regex]::Matches($commonMainDeps, [regex]::Escape($dep))).Count
}

$ktFiles = Get-ChildItem -LiteralPath $commonMain -Recurse -Filter *.kt
$sourceHits = 0
foreach ($file in $ktFiles) {
    $text = Get-Content -Raw -LiteralPath $file.FullName
    foreach ($token in $forbiddenSourceTokens) {
        $sourceHits += ([regex]::Matches($text, [regex]::Escape($token))).Count
    }
}

$checks = $forbiddenDeps.Count + ($ktFiles.Count * $forbiddenSourceTokens.Count)
Write-Output "HOLDER_BOUNDARY_CHECK_COUNT=$checks"
Write-Output "FORBIDDEN_DEP_HITS=$depHits"
Write-Output "FORBIDDEN_SOURCE_HITS=$sourceHits"
Write-Output "COMMONMAIN_KT_FILES=$($ktFiles.Count)"

if ($depHits -ne 0 -or $sourceHits -ne 0) {
    throw "holder-wiring dependency boundary guard failed: deps=$depHits source=$sourceHits"
}
Write-Output 'HOLDER_BOUNDARY_GUARD=GREEN'
