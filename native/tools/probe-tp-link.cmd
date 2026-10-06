@echo off
setlocal EnableExtensions

title Matrix3 Native Builder - TP Link Visual Proof
set "PY_BOOTSTRAP=%~dp0ensure-tp-visual-python.ps1"
set "PROBE_SCRIPT=%~dp0probe-tp-link.ps1"

echo ================================================
echo Matrix3 - Twilight Princess Link visual proof
echo ================================================
echo.

if not exist "%PY_BOOTSTRAP%" (
    echo ERROR: TP Link visual Python bootstrap was not found:
    echo   %PY_BOOTSTRAP%
    goto :fail
)

if not exist "%PROBE_SCRIPT%" (
    echo ERROR: TP Link probe script was not found:
    echo   %PROBE_SCRIPT%
    goto :fail
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PY_BOOTSTRAP%"
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" goto :python_fail

echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PROBE_SCRIPT%"
set "RC=%ERRORLEVEL%"

if "%RC%"=="0" goto :generated

echo.
echo ================================================
echo TP LINK VISUAL PROOF FAILED  (exit %RC%)
echo ================================================
echo Send me the error shown above; no manual asset extraction is needed.
echo.
pause
exit /b %RC%

:python_fail
echo.
echo ================================================
echo TP LINK VISUAL PYTHON SETUP FAILED  (exit %RC%)
echo ================================================
echo Send me the error shown above; the donor build does not need to be rerun.
echo.
pause
exit /b %RC%

:generated
echo.
echo ================================================
echo TP LINK VISUAL PROOF GENERATED
echo ================================================
echo The combined idle / walk / sword GIF should open automatically.
echo Cyan 0x9 marks GameCube Link's left hand. Red 0xA marks the active left item/weapon joint.
echo The TP al_swb wooden sword model is attached to 0xA for the sword-socket diagnostic.
echo The right-side 0xE/0xF pair remains the alternate right hand/item socket.
echo This is NOT a visual PASS until the generated GIF is inspected and accepted.
echo Matrix3 in-client rendering remains the next bundle only after that acceptance.
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
