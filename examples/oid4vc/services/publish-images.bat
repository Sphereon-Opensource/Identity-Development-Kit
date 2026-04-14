@echo off
REM Publish IDK oid4vc example service images to a Docker registry.
REM SNAPSHOT guard: refuses to push *-SNAPSHOT unless --allow-snapshot.
REM
REM Usage:
REM   docker login docker.io
REM   publish-images.bat
REM   publish-images.bat --allow-snapshot
REM   publish-images.bat --dirty
REM   set REGISTRY=ghcr.io/sphereon && publish-images.bat

setlocal enabledelayedexpansion
cd /d "%~dp0"

set ALLOW_SNAPSHOT=0
set ALLOW_DIRTY=0
:argloop
if "%~1"=="" goto argdone
if "%~1"=="--allow-snapshot" set ALLOW_SNAPSHOT=1
if "%~1"=="--dirty" set ALLOW_DIRTY=1
shift
goto argloop
:argdone

set "IDK_ROOT=%~dp0..\..\.."
for /f "tokens=2 delims==" %%v in ('findstr /b /c:"version=" "%IDK_ROOT%\gradle.properties"') do set IDK_VERSION=%%v
for /f %%s in ('git -C "%IDK_ROOT%" rev-parse --short HEAD') do set GIT_SHA=%%s
if not defined REGISTRY set REGISTRY=sphereon

if %ALLOW_DIRTY%==0 (
    git -C "%IDK_ROOT%" diff-index --quiet HEAD --
    if errorlevel 1 (
        echo ERROR: IDK source tree has uncommitted changes. Pass --dirty to override.
        exit /b 1
    )
)

set IS_SNAPSHOT=0
echo %IDK_VERSION% | findstr /E "\-SNAPSHOT" >nul && set IS_SNAPSHOT=1

if %IS_SNAPSHOT%==1 if %ALLOW_SNAPSHOT%==0 (
    echo ERROR: Refusing to push SNAPSHOT version %IDK_VERSION%. Pass --allow-snapshot to override.
    exit /b 1
)

call build-images.bat
if errorlevel 1 exit /b 1

for %%s in (oauth2-as oid4vci-issuer oid4vp-verifier oid4vc-webapp) do (
    docker push "%REGISTRY%/idk-%%s:%IDK_VERSION%" || exit /b 1
    docker push "%REGISTRY%/idk-%%s:%GIT_SHA%" || exit /b 1
    if !IS_SNAPSHOT!==0 (
        docker push "%REGISTRY%/idk-%%s:latest" || exit /b 1
    ) else (
        echo Skipping :latest push for SNAPSHOT %REGISTRY%/idk-%%s
    )
)

echo.
echo Published images at version %IDK_VERSION% to %REGISTRY%.
