# Setup script for GraalVM example
# Copies Gradle wrapper from project root

Write-Host "Setting up GraalVM example..."

# Get directories
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
# Go up 5 levels: graalvm -> examples -> ktor-server-kotlin-inject -> plugins -> server -> ktor -> project-root
$projectRoot = Resolve-Path "$scriptDir\..\..\..\..\..\.."

Write-Host "Project root: $projectRoot"
Write-Host "Example dir: $scriptDir"

# Copy Gradle wrapper files
if (Test-Path "$projectRoot\gradlew")
{
    Copy-Item "$projectRoot\gradlew" "$scriptDir\" -Force
    Write-Host "Copied gradlew"
}
else
{
    Write-Host "ERROR: gradlew not found in project root"
    exit 1
}

if (Test-Path "$projectRoot\gradlew.bat")
{
    Copy-Item "$projectRoot\gradlew.bat" "$scriptDir\" -Force
    Write-Host "Copied gradlew.bat"
}

if (Test-Path "$projectRoot\gradle")
{
    Copy-Item "$projectRoot\gradle" "$scriptDir\" -Recurse -Force
    Write-Host "Copied gradle directory"
}
else
{
    Write-Host "ERROR: gradle directory not found in project root"
    exit 1
}

Write-Host ""
Write-Host "Setup complete! You can now build with Docker"
