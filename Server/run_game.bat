@echo off
title MATRIX3 - GAME SERVER
cd /d "%~dp0"
call "%~dp0gradlew.bat" runGame
echo.
pause
