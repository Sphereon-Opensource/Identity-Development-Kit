@echo off
REM Build fat JARs and Docker images for the IDK oid4vc example services.
REM Does NOT start anything, does NOT push.
REM
REM Registry override: set REGISTRY=ghcr.io/sphereon && build-images.bat

setlocal enabledelayedexpansion
cd /d "%~dp0"

set "IDK_ROOT=%~dp0..\..\.."
for /f "tokens=2 delims==" %%v in ('findstr /b /c:"version=" "%IDK_ROOT%\gradle.properties"') do set IDK_VERSION=%%v
for /f %%s in ('git -C "%IDK_ROOT%" rev-parse --short HEAD 2^>nul') do set GIT_SHA=%%s
if not defined GIT_SHA set GIT_SHA=unknown
if not defined REGISTRY set REGISTRY=sphereon

echo IDK_VERSION=%IDK_VERSION%
echo GIT_SHA=%GIT_SHA%
echo REGISTRY=%REGISTRY%

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

if not exist jars mkdir jars
for %%j in ("%IDK_ROOT%\services\oauth2-as\rest\build\libs\*-all.jar")         do copy /Y "%%j" "jars\oauth2-as.jar" >nul
for %%j in ("%IDK_ROOT%\services\oid4vci-issuer\rest\build\libs\*-all.jar")    do copy /Y "%%j" "jars\oid4vci-issuer.jar" >nul
for %%j in ("%IDK_ROOT%\services\oid4vp-verifier\rest\build\libs\*-all.jar")   do copy /Y "%%j" "jars\oid4vp-verifier.jar" >nul
for %%j in ("%IDK_ROOT%\examples\oid4vc\webapp\server\build\libs\*-all.jar")   do copy /Y "%%j" "jars\webapp.jar" >nul

call :build oauth2-as        jars/oauth2-as.jar
call :build oid4vci-issuer   jars/oid4vci-issuer.jar
call :build oid4vp-verifier  jars/oid4vp-verifier.jar
call :build oid4vc-webapp    jars/webapp.jar
exit /b 0

:build
echo Building %REGISTRY%/idk-%~1 from %~2...
docker build -f Dockerfile.service --build-arg JAR_FILE=%~2 ^
    -t "%REGISTRY%/idk-%~1:%IDK_VERSION%" ^
    -t "%REGISTRY%/idk-%~1:latest" ^
    -t "%REGISTRY%/idk-%~1:%GIT_SHA%" ^
    .
if errorlevel 1 exit /b 1
exit /b 0
