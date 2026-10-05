@echo off
setlocal
set "TOOL=%~dp0native\tools\NativeBuildTool.ps1"

if not exist "%TOOL%" (
    echo Matrix3 Native Builder is missing:
    echo %TOOL%
    pause
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%TOOL%"
