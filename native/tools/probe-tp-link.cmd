@echo off
setlocal EnableExtensions

title Matrix3 Native Builder - TP Link Visual Proof
set "PROBE_SCRIPT=%~dp0probe-tp-link.ps1"

echo ================================================
echo Matrix3 - Twilight Princess Link visual proof
echo ================================================
echo.

if not exist "%PROBE_SCRIPT%" (
    echo ERROR: TP Link probe script was not found:
    echo   %PROBE_SCRIPT%
    goto :fail
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PROBE_SCRIPT%"
set "RC=%ERRORLEVEL%"

if "%RC%"=="0" goto :success

echo.
echo ================================================
echo TP LINK VISUAL PROOF FAILED  (exit %RC%)
echo ================================================
echo Send me the error shown above; no manual asset extraction is needed.
echo.
pause
exit /b %RC%

:success
echo.
echo ================================================
echo TP LINK VISUAL PROOF PASS
echo ================================================
echo The combined idle / walk / sword GIF should open automatically.
echo Verify that Link renders correctly and the red 0xF marker follows his weapon joint.
echo This is the local asset proof; Matrix3 in-client rendering is the next bundle.
echo.
pause
exit /b 0

:fail
echo.
echo ================================================
echo TP LINK VISUAL PROOF COULD NOT START
echo ================================================
echo.
pause
exit /b 1
