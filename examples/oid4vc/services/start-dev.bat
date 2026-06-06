@echo off
REM Start the IDK services environment for wallet testing (DEV MODE).
REM
REM Dev mode: builds fat JARs from the local IDK source tree, then builds and
REM starts Docker images from those JARs. Use this when iterating on IDK code.
REM For users running published images, use start.bat instead.
REM
REM Usage:
REM   start-dev.bat                                              Auto-detect LAN IP, default profile
REM   start-dev.bat https://my.ngrok.app                         Pass URL as argument
REM   start-dev.bat https://my.ngrok.app haip                    Layer the HAIP conformance profile
REM   set EXTERNAL_BASE_URL=http://myhost:8080 && start-dev.bat  Use env var
REM
REM Conformance profile values for the second argument:
REM   default       Plain demo (did:jwk verifier, plain AS); identical to omitting the arg.
REM   did-jwk       Explicit did:jwk verifier prefix, plain AS.
REM   x509-san-dns  x509_san_dns verifier prefix (OID4VP plan), plain AS.
REM   x509-hash     x509_hash verifier prefix (OID4VP plan), plain AS.
REM   haip          HAIP conformance combo: x509_hash verifier + HAIP-shaped AS auth methods.

setlocal enabledelayedexpansion
cd /d "%~dp0"

REM Resolve EXTERNAL_BASE_URL via shared helper
for /f "usebackq delims=" %%u in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lib\resolve-base-url.ps1" -BaseUrl "%~1"`) do set EXTERNAL_BASE_URL=%%u
if not defined EXTERNAL_BASE_URL (
    echo Failed to resolve EXTERNAL_BASE_URL.
    exit /b 1
)
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
if not defined PROFILE_NAME (
    echo Failed to resolve conformance profile.
    exit /b 1
)
echo PROFILE=!PROFILE_NAME!
if defined COMPOSE_PROFILE_ARGS if not "!COMPOSE_PROFILE_ARGS!"=="" echo PROFILE_ENV_FILES=!COMPOSE_PROFILE_ARGS!

REM Resolve IDK_VERSION from IDK gradle.properties.
REM Normalize IDK_ROOT to an absolute path — `pushd` + `call gradlew.bat` against
REM a `..\..\..`-relative path fails to find gradlew.bat in some cmd hosts.
for %%d in ("%~dp0..\..\..") do set "IDK_ROOT=%%~fd"
for /f "tokens=2 delims==" %%v in ('findstr /b /c:"version=" "%IDK_ROOT%\gradle.properties"') do set IDK_VERSION=%%v
echo IDK_VERSION=%IDK_VERSION%
echo Dev mode: building from local IDK source at %IDK_ROOT%

REM Write .env
(
    echo EXTERNAL_BASE_URL=%EXTERNAL_BASE_URL%
    echo IDK_VERSION=%IDK_VERSION%
) > .env

REM Template VCT files
if errorlevel 1 exit /b 1

REM Build fat JARs — call gradlew with an absolute path so it works regardless
REM of which cmd host invoked the bat (some hosts don't search cwd for .bat files).
echo Building fat JARs from %IDK_ROOT%...
pushd "%IDK_ROOT%"
call "%IDK_ROOT%\gradlew.bat" ^
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
echo   Profile:           !PROFILE_NAME!
if defined COMPOSE_PROFILE_ARGS if not "!COMPOSE_PROFILE_ARGS!"=="" echo   Layered env files: !COMPOSE_PROFILE_ARGS!
echo   Issuer identifier: %EXTERNAL_BASE_URL%/oid4vci
echo   Issuer metadata:   %EXTERNAL_BASE_URL%/.well-known/openid-credential-issuer/oid4vci
echo   AS issuer:         %EXTERNAL_BASE_URL%/auth
echo   AS discovery:      %EXTERNAL_BASE_URL%/.well-known/oauth-authorization-server/auth
echo   Login form:        %EXTERNAL_BASE_URL%/auth/login
echo.

docker compose !COMPOSE_PROFILE_ARGS! up -d --build

echo.
echo Services starting. Check health:
echo   curl %EXTERNAL_BASE_URL%/auth/health
