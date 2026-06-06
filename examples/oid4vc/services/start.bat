@echo off
REM Start the IDK services environment for wallet testing (PUBLISHED IMAGES).
REM
REM Pulls sphereon/idk-* images from Docker Hub and starts them. No Gradle
REM build, no IDK source tree required.
REM
REM For IDK contributors iterating on source, use start-dev.bat instead.
REM
REM Usage:
REM   start.bat                                          Auto-detect LAN IP, default profile
REM   start.bat https://my.ngrok.app                     Pass URL as argument
REM   start.bat https://my.ngrok.app haip                Layer the HAIP conformance profile
REM   set IDK_VERSION=0.24.0 && start.bat                Pin a specific version
REM
REM Conformance profile values for the second argument:
REM   default       Plain demo (did:jwk verifier, plain AS); identical to omitting the arg.
REM   did-jwk       Explicit did:jwk verifier prefix, plain AS.
REM   x509-san-dns  x509_san_dns verifier prefix (OID4VP plan), plain AS.
REM   x509-hash     x509_hash verifier prefix (OID4VP plan), plain AS.
REM   haip          HAIP conformance combo: x509_hash verifier + HAIP-shaped AS auth methods.

setlocal enabledelayedexpansion
cd /d "%~dp0"

for /f "usebackq delims=" %%u in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lib\resolve-base-url.ps1" -BaseUrl "%~1"`) do set EXTERNAL_BASE_URL=%%u
if not defined EXTERNAL_BASE_URL ( echo Failed to resolve EXTERNAL_BASE_URL. & exit /b 1 )
echo EXTERNAL_BASE_URL=%EXTERNAL_BASE_URL%

REM Resolve conformance profile (second positional arg)
set "PROFILE_NAME="
set "COMPOSE_PROFILE_ARGS="
set "_profileLine=0"
for /f "usebackq delims=" %%l in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lib\resolve-profile-args.ps1" -RawProfile "%~2"`) do (
    set /a _profileLine+=1
    if !_profileLine! equ 1 set "PROFILE_NAME=%%l"
    if !_profileLine! equ 2 set "COMPOSE_PROFILE_ARGS=%%l"
)
if not defined PROFILE_NAME ( echo Failed to resolve conformance profile. & exit /b 1 )
echo PROFILE=!PROFILE_NAME!
if defined COMPOSE_PROFILE_ARGS if not "!COMPOSE_PROFILE_ARGS!"=="" echo PROFILE_ENV_FILES=!COMPOSE_PROFILE_ARGS!

REM Version resolution: env var > IDK gradle.properties (if present) > "latest"
if not defined IDK_VERSION (
    set "GP=%~dp0..\..\..\gradle.properties"
    if exist "!GP!" (
        for /f "tokens=2 delims==" %%v in ('findstr /b /c:"version=" "!GP!"') do set IDK_VERSION=%%v
        echo IDK_VERSION=!IDK_VERSION! ^(resolved from !GP!^)
    ) else (
        set IDK_VERSION=latest
        echo WARN: IDK source not present and IDK_VERSION not set; using :latest
    )
) else (
    echo IDK_VERSION=%IDK_VERSION% ^(from environment^)
)

(
    echo EXTERNAL_BASE_URL=%EXTERNAL_BASE_URL%
    echo IDK_VERSION=%IDK_VERSION%
) > .env

if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lib\generate-keystores.ps1"
if errorlevel 1 exit /b 1

echo.
echo Starting IDK services from published images (sphereon/idk-*:%IDK_VERSION%)...
echo   Profile:           !PROFILE_NAME!
if defined COMPOSE_PROFILE_ARGS if not "!COMPOSE_PROFILE_ARGS!"=="" echo   Layered env files: !COMPOSE_PROFILE_ARGS!
echo   Issuer identifier: %EXTERNAL_BASE_URL%/oid4vci
echo   AS issuer:         %EXTERNAL_BASE_URL%/auth
echo.

docker compose !COMPOSE_PROFILE_ARGS! pull
docker compose !COMPOSE_PROFILE_ARGS! up -d

echo.
echo Services starting. Check health:
echo   curl %EXTERNAL_BASE_URL%/auth/health
