@echo off
REM Start the IDK services environment for wallet testing (DEV MODE).
REM
REM Dev mode: builds fat JARs from the local IDK source tree, then builds and
REM starts Docker images from those JARs. Use this when iterating on IDK code.
REM For users running published images, use start.bat instead.
REM
REM Usage:
REM   start-dev.bat                                              Auto-detect LAN IP
REM   start-dev.bat https://my.ngrok.app                         Pass URL as argument
REM   set EXTERNAL_BASE_URL=http://myhost:8080 && start-dev.bat  Use env var

setlocal enabledelayedexpansion
cd /d "%~dp0"

REM Resolve EXTERNAL_BASE_URL via shared helper
for /f "usebackq delims=" %%u in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lib\resolve-base-url.ps1" -BaseUrl "%~1"`) do set EXTERNAL_BASE_URL=%%u
if not defined EXTERNAL_BASE_URL (
    echo Failed to resolve EXTERNAL_BASE_URL.
    exit /b 1
)
echo EXTERNAL_BASE_URL=%EXTERNAL_BASE_URL%

REM Resolve IDK_VERSION from IDK gradle.properties
set "IDK_ROOT=%~dp0..\..\.."
for /f "tokens=2 delims==" %%v in ('findstr /b /c:"version=" "%IDK_ROOT%\gradle.properties"') do set IDK_VERSION=%%v
echo IDK_VERSION=%IDK_VERSION%
echo Dev mode: building from local IDK source at %IDK_ROOT%

REM Write .env
(
    echo EXTERNAL_BASE_URL=%EXTERNAL_BASE_URL%
    echo IDK_VERSION=%IDK_VERSION%
) > .env

REM Template VCT files
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lib\template-vcts.ps1" -BaseUrl "%EXTERNAL_BASE_URL%"
if errorlevel 1 exit /b 1

REM Build fat JARs
echo Building fat JARs from %IDK_ROOT%...
pushd "%IDK_ROOT%"
call gradlew.bat ^
    :services-oauth2-as-rest:buildFatJar ^
    :services-oid4vci-issuer-rest:buildFatJar ^
    :services-oid4vp-verifier-rest:buildFatJar ^
    :examples-oid4vc-webapp-server:buildFatJar ^
    --no-daemon --parallel ^
    -Dkotlin.mpp.enabledTargets=jvm ^
    -Dkotlin.native.ignoreDisabledTargets=true
if errorlevel 1 ( popd & exit /b 1 )
popd

REM Copy fat JARs
if not exist jars mkdir jars
for %%j in ("%IDK_ROOT%\services\oauth2-as\rest\build\libs\*-all.jar")         do copy /Y "%%j" "jars\oauth2-as.jar" >nul
for %%j in ("%IDK_ROOT%\services\oid4vci-issuer\rest\build\libs\*-all.jar")    do copy /Y "%%j" "jars\oid4vci-issuer.jar" >nul
for %%j in ("%IDK_ROOT%\services\oid4vp-verifier\rest\build\libs\*-all.jar")   do copy /Y "%%j" "jars\oid4vp-verifier.jar" >nul
for %%j in ("%IDK_ROOT%\examples\oid4vc\webapp\server\build\libs\*-all.jar")   do copy /Y "%%j" "jars\webapp.jar" >nul

REM Generate keystores
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lib\generate-keystores.ps1"
if errorlevel 1 exit /b 1

echo.
echo Starting IDK services environment...
echo   Issuer identifier: %EXTERNAL_BASE_URL%/oid4vci
echo   Issuer metadata:   %EXTERNAL_BASE_URL%/.well-known/openid-credential-issuer/oid4vci
echo   AS issuer:         %EXTERNAL_BASE_URL%/auth
echo   AS discovery:      %EXTERNAL_BASE_URL%/.well-known/oauth-authorization-server/auth
echo   Login form:        %EXTERNAL_BASE_URL%/auth/login
echo.

docker compose up -d --build

echo.
echo Services starting. Check health:
echo   curl %EXTERNAL_BASE_URL%/auth/health
