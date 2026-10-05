@echo off
setlocal EnableExtensions

title Matrix3 Native Builder - Twilight Princess Link
set "TP_SCRIPT=%~dp0build-tp.ps1"

echo ================================================
echo Matrix3 - Twilight Princess Link donor prep + build
echo ================================================
echo.

if not exist "%TP_SCRIPT%" (
    echo ERROR: TP builder script was not found:
    echo   %TP_SCRIPT%
    goto :fail
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%TP_SCRIPT%"
set "RC=%ERRORLEVEL%"

if "%RC%"=="0" goto :success

echo.
echo ================================================
echo TP LINK PREP / BUILD FAILED  (exit %RC%)
echo ================================================
echo The log above is the only thing you need to send me.
echo.
pause
exit /b %RC%

:success
echo.
echo ================================================
echo TP LINK DONOR BUILD SUCCESS
echo ================================================
echo The pinned GZ2E01 Twilight Princess decomp is ready for the next Matrix3 slice.
echo This does NOT mean TP Link is in Matrix3 yet.
echo.
pause
exit /b 0

:fail
echo.
echo ================================================
echo TP LINK BUILD COULD NOT START
echo ================================================
echo.
pause
exit /b 1
