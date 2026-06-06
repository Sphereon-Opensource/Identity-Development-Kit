@echo off
REM Start the IDK services environment for OIDF conformance testing (DEV MODE).
REM
REM Same as start-dev.bat, but layers docker-compose.conformance.yaml on top so Caddy
REM also exposes a TLS-terminating :8443 listener with the BCP 195 cipher list the
REM OIDF FAPI2 suite's RequireOnlyBCP195RecommendedCiphersForTLS12 check expects.
REM The wallet/RP under test reaches Caddy via an ngrok TLS-passthrough tunnel pointed
REM at localhost:8443 (Pay-as-you-go ngrok plan; Personal blocks TLS endpoints).
REM
REM Regular contributors who don't run the conformance suite should use start-dev.bat —
REM it keeps Caddy HTTP-only on :8080 and skips ACME / cert provisioning entirely.
REM
REM Usage:
REM   start-conformance-dev.bat https://sphereon-oid4vc.ngrok.dev               default: haip
REM   start-conformance-dev.bat https://sphereon-oid4vc.ngrok.dev x509-san-dns  OID4VP plan
REM
REM Default profile is haip because non-HAIP conformance runs are rare — most OIDF
REM plans flagged at conformance scope (FAPI2, VCI HAIP, VP HAIP) require the HAIP-
REM shaped AS (attest_jwt_client_auth + DPoP required + attestation required). Pass a
REM different profile name explicitly if you're running a non-HAIP suite.
REM
REM Conformance profile values for the second argument:
REM   haip               (DEFAULT) HAIP conformance combo (encryption SUPPORTED).
REM   haip-enc-required  HAIP combo + credential_response_encryption.encryption_required=true.
REM                      Use for OIDF plans that specifically test the required-encryption path;
REM                      most plans run with plain `haip` because the suite's
REM                      `vci_credential_encryption=encrypted` variant works in either mode.
REM   default            Plain demo (did:jwk verifier, plain AS); rarely useful here.
REM   did-jwk            Explicit did:jwk verifier prefix, plain AS.
REM   x509-san-dns       x509_san_dns verifier prefix (OID4VP plan), plain AS.
REM   x509-hash          x509_hash verifier prefix (OID4VP plan), plain AS.
REM
REM Prereqs:
REM   1. ngrok TLS tunnel running:
REM        ngrok tls --domain=sphereon-oid4vc.ngrok.dev localhost:8443
REM   2. The hostname in Caddyfile.conformance matches the ngrok domain you forward to.

setlocal enabledelayedexpansion
cd /d "%~dp0"

REM Resolve EXTERNAL_BASE_URL via shared helper
for /f "usebackq delims=" %%u in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lib\resolve-base-url.ps1" -BaseUrl "%~1"`) do set EXTERNAL_BASE_URL=%%u
if not defined EXTERNAL_BASE_URL (
    echo Failed to resolve EXTERNAL_BASE_URL.
    exit /b 1
)
echo EXTERNAL_BASE_URL=%EXTERNAL_BASE_URL%

REM Resolve conformance profile (second positional arg). Conformance defaults to haip —
REM the only profile most conformance plans accept.
set "_rawProfile=%~2"
if "%_rawProfile%"=="" set "_rawProfile=haip"
set "PROFILE_NAME="
set "COMPOSE_PROFILE_ARGS="
set "_profileLine=0"
for /f "usebackq delims=" %%l in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lib\resolve-profile-args.ps1" -RawProfile "%_rawProfile%"`) do (
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
for %%d in ("%~dp0..\..\..") do set "IDK_ROOT=%%~fd"
for /f "tokens=2 delims==" %%v in ('findstr /b /c:"version=" "%IDK_ROOT%\gradle.properties"') do set IDK_VERSION=%%v
echo IDK_VERSION=%IDK_VERSION%
echo Conformance dev mode: building from local IDK source at %IDK_ROOT%

REM Write .env
(
    echo EXTERNAL_BASE_URL=%EXTERNAL_BASE_URL%
    echo IDK_VERSION=%IDK_VERSION%
) > .env

REM Template VCT files
if errorlevel 1 exit /b 1

REM Build fat JARs
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
echo Starting IDK services environment with conformance TLS termination...
echo   Profile:           !PROFILE_NAME!
if defined COMPOSE_PROFILE_ARGS if not "!COMPOSE_PROFILE_ARGS!"=="" echo   Layered env files: !COMPOSE_PROFILE_ARGS!
echo   Compose overlay:   docker-compose.conformance.yaml
echo   Caddy config:      Caddyfile.conformance (BCP 195 ciphers on :8443)
echo   Issuer identifier: %EXTERNAL_BASE_URL%/oid4vci
echo   Issuer metadata:   %EXTERNAL_BASE_URL%/.well-known/openid-credential-issuer/oid4vci
echo   AS issuer:         %EXTERNAL_BASE_URL%/auth
echo   AS discovery:      %EXTERNAL_BASE_URL%/.well-known/oauth-authorization-server/auth
echo   Login form:        %EXTERNAL_BASE_URL%/auth/login
echo.
echo   ngrok forward:     ngrok tls --domain=^<your-domain^> localhost:8443
echo.

docker compose -f docker-compose.yaml -f docker-compose.conformance.yaml !COMPOSE_PROFILE_ARGS! up -d --build

echo.
echo Services starting. Check health:
echo   curl %EXTERNAL_BASE_URL%/auth/health
