# PowerShell test script for GraalVM Native Image example

$BaseUrl = "http://localhost:8080"

Write-Host "===================================" -ForegroundColor Green
Write-Host "Testing GraalVM Native Image Example" -ForegroundColor Green
Write-Host "===================================" -ForegroundColor Green
Write-Host

# Test health endpoint
Write-Host "1. Testing /health endpoint..." -ForegroundColor Yellow
Invoke-RestMethod -Uri "$BaseUrl/health" | ConvertTo-Json
Write-Host

# Test root endpoint
Write-Host "2. Testing / endpoint..." -ForegroundColor Yellow
Invoke-WebRequest -Uri "$BaseUrl/" | Select-Object -ExpandProperty Content
Write-Host
Write-Host

# Test config endpoint
Write-Host "3. Testing /config endpoint..." -ForegroundColor Yellow
Invoke-RestMethod -Uri "$BaseUrl/config" | ConvertTo-Json
Write-Host

# Test user endpoint
Write-Host "4. Testing /user endpoint..." -ForegroundColor Yellow
$headers = @{
    "X-Tenant-ID" = "acme-corp"
    "X-User-ID" = "john@acme.com"
}
Invoke-RestMethod -Uri "$BaseUrl/user/john" -Headers $headers | ConvertTo-Json
Write-Host

# Test session endpoint
Write-Host "5. Testing /session endpoint..." -ForegroundColor Yellow
Invoke-RestMethod -Uri "$BaseUrl/session" -Headers $headers | ConvertTo-Json
Write-Host

# Test system info endpoint
Write-Host "6. Testing /info endpoint..." -ForegroundColor Yellow
Invoke-RestMethod -Uri "$BaseUrl/info" | ConvertTo-Json
Write-Host

Write-Host "===================================" -ForegroundColor Green
Write-Host "All tests completed!" -ForegroundColor Green
Write-Host "===================================" -ForegroundColor Green
