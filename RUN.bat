@echo off
setlocal
cd /d "%~dp0"
title Hospital Patient Registration System

echo ================================================
echo   Hospital Patient Registration System
echo ================================================
echo.
where java >nul 2>nul
if errorlevel 1 (
  echo Java JDK 17 or newer was not found.
  echo Install JDK from: https://www.oracle.com/java/technologies/downloads/
  echo Then close and reopen this window and double-click RUN.bat again.
  pause
  exit /b 1
)
java -version

set "MVN=%~dp0.tools\apache-maven-3.9.11\bin\mvn.cmd"
if not exist "%MVN%" (
  where mvn >nul 2>nul
  if not errorlevel 1 set "MVN=mvn"
)
if not exist "%MVN%" if /i not "%MVN%"=="mvn" (
  echo.
  echo First-time setup: downloading Maven automatically. Internet is required.
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; New-Item -ItemType Directory -Force '.tools' ^| Out-Null; Invoke-WebRequest 'https://archive.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.zip' -OutFile '.tools\maven.zip'; Expand-Archive -Force '.tools\maven.zip' '.tools'; Remove-Item '.tools\maven.zip'"
  if errorlevel 1 (
    echo Maven download failed. Check internet connection and run RUN.bat again.
    pause
    exit /b 1
  )
)
if exist "%~dp0.tools\apache-maven-3.9.11\bin\mvn.cmd" set "MVN=%~dp0.tools\apache-maven-3.9.11\bin\mvn.cmd"

echo.
echo Starting server. Keep the server window open while using the project.
start "Hospital Project Server" cmd /k "cd /d "%~dp0" ^&^& "%MVN%" clean compile exec:java"
echo Waiting for the website to start...
timeout /t 12 /nobreak >nul
start "" "http://localhost:8080"
echo Browser opened. If it does not load yet, wait a few seconds and refresh.
echo You can close this window now, but do NOT close the server window.
pause
