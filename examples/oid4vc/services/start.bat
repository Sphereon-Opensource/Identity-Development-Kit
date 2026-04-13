@echo off
REM Start the IDK services environment for wallet testing.
REM
REM Usage:
REM   start.bat                                              Auto-detect LAN IP
REM   start.bat https://my.ngrok.app                         Pass URL as argument
REM   set EXTERNAL_BASE_URL=http://myhost:8080 && start.bat  Use env var

cd /d "%~dp0"

REM Accept EXTERNAL_BASE_URL as first argument
if not "%~1"=="" set EXTERNAL_BASE_URL=%~1

REM Detect LAN IP if EXTERNAL_BASE_URL not provided
if defined EXTERNAL_BASE_URL goto :have_url

REM Auto-detect LAN IP via PowerShell
for /f "tokens=*" %%i in ('powershell -NoProfile -Command "(Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notmatch 'Loopback' -and $_.PrefixOrigin -ne 'WellKnown' } | Sort-Object -Property InterfaceMetric | Select-Object -First 1).IPAddress"') do set LAN_IP=%%i

if not defined LAN_IP (
    echo Could not auto-detect LAN IP. Set EXTERNAL_BASE_URL manually:
    echo   set EXTERNAL_BASE_URL=http://^<your-ip^>:8080 ^&^& start.bat
    exit /b 1
)

set EXTERNAL_BASE_URL=http://%LAN_IP%:8080
echo Auto-detected LAN IP: %LAN_IP%

:have_url
echo EXTERNAL_BASE_URL=%EXTERNAL_BASE_URL%

REM Write .env for docker-compose
echo EXTERNAL_BASE_URL=%EXTERNAL_BASE_URL%> .env

REM Template VCT type metadata with resolved external URL
if not exist "vct\resolved" mkdir "vct\resolved"
for %%f in (vct\*.json) do (
    powershell -NoProfile -Command "(Get-Content '%%f' -Raw) -replace '\"vct\": \"TestCredential\"', '\"vct\": \"%EXTERNAL_BASE_URL%/oid4vci/vct/TestCredential\"' -replace 'EXTERNAL_BASE_URL', '%EXTERNAL_BASE_URL%' | Set-Content 'vct\resolved\%%~nxf'"
)

echo.
echo Starting IDK services environment...
echo   Issuer identifier: %EXTERNAL_BASE_URL%/oid4vci
echo   Issuer metadata:   %EXTERNAL_BASE_URL%/.well-known/openid-credential-issuer/oid4vci
echo   AS issuer:          %EXTERNAL_BASE_URL%/auth
echo   AS discovery:       %EXTERNAL_BASE_URL%/.well-known/oauth-authorization-server/auth
echo   Login form:         %EXTERNAL_BASE_URL%/auth/login
echo.

docker compose up -d --build

echo.
echo Services starting. Check health:
echo   curl %EXTERNAL_BASE_URL%/auth/health
