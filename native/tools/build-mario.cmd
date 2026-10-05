@echo off
setlocal EnableExtensions

title Matrix3 Native Builder - Mario
set "MSYS2_SHELL=%MATRIX3_MSYS2_SHELL%"
if not defined MSYS2_SHELL set "MSYS2_SHELL=C:\msys64\msys2_shell.cmd"
set "BRIDGE_DIR=%~dp0..\sm64-bridge"

echo ================================================
echo Matrix3 - Mario native build + combat test
echo ================================================
echo.

if not exist "%MSYS2_SHELL%" (
    echo ERROR: MSYS2 shell was not found at:
    echo   %MSYS2_SHELL%
    echo.
    echo If MSYS2 is installed elsewhere, set MATRIX3_MSYS2_SHELL to msys2_shell.cmd.
    goto :fail
)

if not exist "%BRIDGE_DIR%\Makefile" (
    echo ERROR: Mario bridge Makefile was not found:
    echo   %BRIDGE_DIR%\Makefile
    goto :fail
)

echo Stopping any stale sm64_bridge.exe so Windows does not lock the output file...
taskkill /F /IM sm64_bridge.exe >nul 2>nul

echo Building from the correct Mario bridge folder:
echo   %BRIDGE_DIR%
echo.

pushd "%BRIDGE_DIR%" >nul
call "%MSYS2_SHELL%" -defterm -here -no-start -mingw64 -c "make bootstrap && make test-combat"
set "RC=%ERRORLEVEL%"
popd >nul

if "%RC%"=="0" goto :success

echo.
echo ================================================
echo MARIO BUILD FAILED  (exit %RC%)
echo ================================================
echo The log above is the only thing you need to send me.
echo.
pause
exit /b %RC%

:success
echo.
echo ================================================
echo MARIO BUILD + TEST SUCCESS
echo ================================================
echo You can launch Matrix3 and test Mario now.
echo.
pause
exit /b 0

:fail
echo.
echo ================================================
echo MARIO BUILD COULD NOT START
echo ================================================
echo.
pause
exit /b 1
