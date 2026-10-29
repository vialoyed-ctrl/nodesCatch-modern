@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\install-runtime.ps1"
if errorlevel 1 (
  echo Runtime setup failed. Check your connection to GitHub and try again.
  pause
  exit /b 1
)
start "" "%~dp0nodesCatchNext.exe"
