@echo off
title MATRIX3 - LOGIN SERVER
cd /d "%~dp0"
call "%~dp0gradlew.bat" runLogin
echo.
pause
