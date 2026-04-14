@echo off
REM Start the IDK services environment for wallet testing (PUBLISHED IMAGES).
REM
REM Pulls sphereon/idk-* images from Docker Hub and starts them. No Gradle
REM build, no IDK source tree required.
REM
REM For IDK contributors iterating on source, use start-dev.bat instead.
REM
REM Usage:
REM   start.bat                                          Auto-detect LAN IP
REM   start.bat https://my.ngrok.app                     Pass URL as argument
REM   set IDK_VERSION=0.24.0 && start.bat                Pin a specific version

setlocal enabledelayedexpansion
cd /d "%~dp0"

for /f "usebackq delims=" %%u in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lib\resolve-base-url.ps1" -BaseUrl "%~1"`) do set EXTERNAL_BASE_URL=%%u
if not defined EXTERNAL_BASE_URL ( echo Failed to resolve EXTERNAL_BASE_URL. & exit /b 1 )
echo EXTERNAL_BASE_URL=%EXTERNAL_BASE_URL%

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

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lib\template-vcts.ps1" -BaseUrl "%EXTERNAL_BASE_URL%"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lib\generate-keystores.ps1"
if errorlevel 1 exit /b 1

echo.
echo Starting IDK services from published images (sphereon/idk-*:%IDK_VERSION%)...
echo   Issuer identifier: %EXTERNAL_BASE_URL%/oid4vci
echo   AS issuer:         %EXTERNAL_BASE_URL%/auth
echo.

docker compose pull
docker compose up -d

echo.
echo Services starting. Check health:
echo   curl %EXTERNAL_BASE_URL%/auth/health
